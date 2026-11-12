package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.timer.TaskSchedule

object NpcRenderer {
    @JvmStatic
    fun prepare(request: NpcSpawnRequest): NpcStart = PreparedNpc(request).also { it.begin() }

    @JvmStatic
    fun prepare(request: NpcSpawnRequest, limits: NpcPresentationLimits): NpcStart =
        PreparedNpc(request, limits).also { it.begin() }
}

private class PreparedNpc(
    private val request: NpcSpawnRequest,
    private val limits: NpcPresentationLimits = NpcPresentationLimits(),
    private val audience: NpcRenderAudience = NpcRenderAudience.Shared,
    private val personalCopy: Boolean = false,
) : NpcStart, NpcHandle, NpcPresentationParent, NpcPresentationCopyControl {
    private val lock = Any()
    private val ready = CompletableFuture<NpcPrepareResult>()
    private val closed = CompletableFuture<Void>()
    private val commands = HashSet<CompletableFuture<Void>>()
    private val parts = ArrayList<NpcItemDisplay>()
    private var attachmentsDone = false
    @Volatile private var stopping = false
    @Volatile private var published = false
    private var presentations: NpcPresentationOwner? = null
    private var cleanupStarted = false
    private var engine: AnimatedNpcHandle? = null

    fun begin() {
        try {
            request.model.parts.forEach { modelPart ->
                val display = NpcItemDisplay(lock)
                parts += display
                display.also {
                    display.updateViewableRule { false }
                    display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                        meta.setItemStack(request.meshes.resolve(modelPart.model))
                        meta.setTransformationInterpolationDuration(2)
                        meta.setViewRange(
                            maxOf(1.25, request.config.retentionDistance / 64 * 1.05).toFloat()
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            synchronized(lock) {
                attachmentsDone = true
                stopping = true
            }
            reject(failure)
            finishClose()
            return
        }
        val position = request.transform.root.position
        val attaching = parts.map { part ->
            try {
                part.setInstance(request.instance, Pos(position.x, position.y, position.z))
                    ?: CompletableFuture.failedFuture<Void>(
                        IllegalStateException("NPC attachment cancelled")
                    )
            } catch (failure: Throwable) {
                CompletableFuture.failedFuture<Void>(failure)
            }
        }
        CompletableFuture.allOf(*attaching.toTypedArray()).whenComplete { _, error ->
            var failure = error
            if (
                failure == null &&
                    parts.any {
                        !it.isActive ||
                            it.isRemoved ||
                            it.instance !== request.instance ||
                            it.chunk == null ||
                            request.instance.getEntityById(it.entityId) !== it
                    }
            )
                failure = IllegalStateException("NPC part attachment failed")
            val prepared =
                synchronized(lock) {
                    attachmentsDone = true
                    if (failure != null) stopping = true
                    if (stopping) false
                    else {
                        try {
                            engine =
                                AnimatedNpcHandle(
                                    lock,
                                    request.model,
                                    parts,
                                    request.transform,
                                    request.sounds,
                                    request.config,
                                    audience,
                                )
                            true
                        } catch (problem: Throwable) {
                            failure = problem
                            stopping = true
                            false
                        }
                    }
                }
            // Completion handlers and host guards are foreign code: never invoke them under lock.
            if (failure != null) reject(failure!!)
            if (prepared) ready.complete(NpcPrepareResult.Ready(this)) else finishClose()
        }
    }

    private fun reject(error: Throwable) {
        ready.complete(
            NpcPrepareResult.Rejected(
                listOf(
                    NpcProblem("NPC_PREPARE_FAILED", "$", error.message ?: "NPC preparation failed")
                )
            )
        )
    }

    private fun finishClose() {
        val closingEngine =
            synchronized(lock) {
                if (!attachmentsDone || cleanupStarted) return
                stopping = true
                cleanupStarted = true
                engine
            }
        var failure: Throwable? = null
        val personalCleanup =
            try {
                presentations?.closeAll() ?: CompletableFuture.completedFuture(null)
            } catch (problem: Throwable) {
                CompletableFuture.failedFuture<Void>(problem)
            }
        try {
            closingEngine?.close()
        } catch (error: Throwable) {
            failure = error
        }
        parts.forEach { part ->
            try {
                part.remove()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        ready.complete(
            NpcPrepareResult.Rejected(
                listOf(NpcProblem("NPC_CANCELLED", "$", "NPC start cancelled"))
            )
        )
        val cleanupFailure = failure
        CompletableFuture.allOf(
                (closingEngine?.drainMovements() ?: CompletableFuture.completedFuture(null))
                    .toCompletableFuture(),
                personalCleanup.toCompletableFuture(),
            )
            .whenComplete { _, movementFailure ->
                val problem = cleanupFailure ?: movementFailure
                if (problem == null) closed.complete(null)
                else closed.completeExceptionally(problem)
            }
    }

    override fun ready(): CompletionStage<NpcPrepareResult> = ready.minimalCompletionStage()

    override fun cancel(): CompletionStage<Void> {
        val pending =
            synchronized(lock) {
                stopping = true
                commands.toList().also { commands.clear() }
            }
        pending.forEach {
            it.completeExceptionally(java.util.concurrent.CancellationException("NPC closed"))
        }
        finishClose()
        return closed.minimalCompletionStage()
    }

    override fun closeAsync() = cancel()

    private fun submit(action: (AnimatedNpcHandle) -> Unit): CompletionStage<Void> = submitAsync {
        action(it)
        CompletableFuture.completedFuture(null)
    }

    private fun submitAsync(
        action: (AnimatedNpcHandle) -> CompletionStage<Void>
    ): CompletionStage<Void> {
        val result = CompletableFuture<Void>()
        synchronized(lock) {
            if (stopping || engine == null)
                return CompletableFuture.failedFuture(IllegalStateException("NPC unavailable"))
            commands += result
        }
        try {
            request.instance
                .scheduler()
                .buildTask {
                    val target = synchronized(lock) { if (stopping) null else engine }
                    try {
                        if (target == null)
                            throw java.util.concurrent.CancellationException("NPC closed")
                        action(target).whenComplete { _, error ->
                            synchronized(lock) { commands.remove(result) }
                            if (error == null) result.complete(null)
                            else result.completeExceptionally(error)
                        }
                    } catch (error: Throwable) {
                        result.completeExceptionally(error)
                        synchronized(lock) { commands.remove(result) }
                    }
                }
                .delay(TaskSchedule.tick(1))
                .schedule()
        } catch (error: Throwable) {
            synchronized(lock) { commands.remove(result) }
            result.completeExceptionally(error)
        }
        return result.minimalCompletionStage()
    }

    override fun publish(): CompletionStage<Void> = submit { handle ->
        if (
            !request.publicationGuard.runIfCurrent(
                Runnable {
                    handle.publishVisible()
                    published = !stopping
                }
            )
        ) {
            cancel()
            throw java.util.concurrent.CancellationException("NPC host generation changed")
        }
    }

    override fun preparePresentation(options: NpcPresentationOptions): NpcPresentationStart {
        if (personalCopy)
            return rejectedPresentation(
                "NPC_PRESENTATION_UNSUPPORTED",
                "Private renderers cannot own presentations",
            )
        val owner =
            synchronized(lock) {
                val target = engine
                if (stopping || !published || target == null) null
                else
                    presentations
                        ?: NpcPresentationOwner(
                                request,
                                target,
                                limits,
                                { !stopping && published },
                                { operation -> request.publicationGuard.runIfCurrent(operation) },
                                { options, guard, current ->
                                    val config =
                                        request.config.copy(
                                            nearIntervalMillis = options.sampleIntervalMillis,
                                            midIntervalMillis = options.sampleIntervalMillis,
                                            farIntervalMillis = options.sampleIntervalMillis,
                                            idleClip = null,
                                            variationClip = null,
                                        )
                                    val child =
                                        PreparedNpc(
                                            request.copy(
                                                transform = target.currentTransform(),
                                                publicationGuard = guard,
                                                sounds = NpcSoundSink { _, _, _ -> },
                                                config = config,
                                            ),
                                            limits,
                                            NpcRenderAudience.Personal(options.player, current),
                                            true,
                                        )
                                    NpcPresentationCopy(child.also { it.begin() }, child, child)
                                },
                            )
                            .also { presentations = it }
            }
                ?: return rejectedPresentation(
                    "NPC_PRESENTATION_PARENT_GONE",
                    "Parent is not published or has closed",
                )
        return owner.prepare(options)
    }

    override fun renderPresentationNow(frame: NpcSampleFrame) {
        val target =
            synchronized(lock) {
                check(!stopping)
                checkNotNull(engine)
            }
        target.renderFrame(frame)
    }

    override fun applyPersonalVisualNow(player: Player, state: NpcViewerState) {
        val target =
            synchronized(lock) {
                check(!stopping)
                checkNotNull(engine)
            }
        target.applyViewerState(player, state)
    }

    override fun submitPresentation(action: (AnimatedNpcHandle) -> Unit) = submit(action)

    override fun applyTransform(transform: NpcRenderTransform) = submitAsync {
        it.applyTransform(transform)
    }

    override fun startAnimation(clipId: String, elapsedMillis: Long) = submit {
        require(elapsedMillis >= 0)
        it.startAnimation(clipId, elapsedMillis)
    }

    override fun stopAnimation(clipId: String?) = submit { it.stopAnimation(clipId) }

    override fun advanceAnimation(elapsedMillis: Long) = submit {
        require(elapsedMillis >= 0)
        it.advanceAnimation(elapsedMillis)
    }

    override fun applyHeadLook(yaw: Double, pitch: Double) = submit {
        require(yaw.isFinite() && pitch.isFinite())
        it.applyHeadLook(yaw, pitch)
    }

    override fun applyViewerState(player: Player, state: NpcViewerState) = submit {
        require(player.isOnline && player.instance === request.instance)
        it.applyViewerState(player, state)
    }

    override fun clearViewerState(player: Player) = submit { it.clearViewerState(player) }

    override fun snapshot(): NpcAnchorSnapshot =
        synchronized(lock) { checkNotNull(engine).snapshot() }
}
