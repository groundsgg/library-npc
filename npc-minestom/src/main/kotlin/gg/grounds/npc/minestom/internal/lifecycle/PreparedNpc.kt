package gg.grounds.npc.minestom.internal.lifecycle

import gg.grounds.npc.core.validation.NpcProblem
import gg.grounds.npc.minestom.NpcClipReservation
import gg.grounds.npc.minestom.NpcHandle
import gg.grounds.npc.minestom.NpcPrepareResult
import gg.grounds.npc.minestom.NpcSpawnRequest
import gg.grounds.npc.minestom.NpcStart
import gg.grounds.npc.minestom.error.NpcOperationException
import gg.grounds.npc.minestom.internal.presentation.NpcPresentationCopy
import gg.grounds.npc.minestom.internal.presentation.NpcPresentationCopyControl
import gg.grounds.npc.minestom.internal.presentation.NpcPresentationOwner
import gg.grounds.npc.minestom.internal.presentation.rejectedPresentation
import gg.grounds.npc.minestom.internal.rendering.NpcItemDisplay
import gg.grounds.npc.minestom.internal.rendering.NpcRenderAudience
import gg.grounds.npc.minestom.internal.rendering.NpcRenderEngine
import gg.grounds.npc.minestom.internal.rendering.NpcSampleFrame
import gg.grounds.npc.minestom.presentation.NpcPresentationLimits
import gg.grounds.npc.minestom.presentation.NpcPresentationOptions
import gg.grounds.npc.minestom.presentation.NpcPresentationStart
import gg.grounds.npc.minestom.spi.NpcSoundSink
import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.timer.TaskSchedule

internal class PreparedNpc(
    private val request: NpcSpawnRequest,
    private val limits: NpcPresentationLimits = NpcPresentationLimits(),
    private val audience: NpcRenderAudience = NpcRenderAudience.Shared,
    private val personalCopy: Boolean = false,
) : NpcStart, NpcHandle, NpcPresentationCopyControl {
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
    private var engine: NpcRenderEngine? = null

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
                                NpcRenderEngine(
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
        // A live engine owns parts until its accepted movement I/O/events have drained.
        if (closingEngine == null || failure != null)
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

    override fun closed(): CompletionStage<Void> = closed.minimalCompletionStage()

    private fun submit(action: (NpcRenderEngine) -> Unit): CompletionStage<Void> = submitAsync {
        action(it)
        CompletableFuture.completedFuture(null)
    }

    private fun submitAsync(
        action: (NpcRenderEngine) -> CompletionStage<Void>
    ): CompletionStage<Void> {
        val result = CompletableFuture<Void>()
        synchronized(lock) {
            if (stopping || engine == null)
                return CompletableFuture.failedFuture(
                    NpcOperationException("NPC_UNAVAILABLE", "NPC has closed or is not ready")
                )
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
                            else result.completeExceptionally(operationFailure(error))
                        }
                    } catch (error: Throwable) {
                        result.completeExceptionally(operationFailure(error))
                        synchronized(lock) { commands.remove(result) }
                    }
                }
                .delay(TaskSchedule.tick(1))
                .schedule()
        } catch (error: Throwable) {
            synchronized(lock) { commands.remove(result) }
            result.completeExceptionally(operationFailure(error))
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
                                        request.config
                                            .toBuilder()
                                            .nearIntervalMillis(options.sampleIntervalMillis)
                                            .midIntervalMillis(options.sampleIntervalMillis)
                                            .farIntervalMillis(options.sampleIntervalMillis)
                                            .idleClip(null)
                                            .variationClip(null)
                                            .build()
                                    val child =
                                        PreparedNpc(
                                            request
                                                .toBuilder()
                                                .transform(target.currentTransform())
                                                .publicationGuard(guard)
                                                .sounds(NpcSoundSink { _, _, _ -> })
                                                .config(config)
                                                .build(),
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

    override fun inheritViewerAdmissionNow(player: Player) {
        val target = synchronized(lock) { checkNotNull(engine) }
        target.inheritViewerAdmission(player)
    }

    override fun viewerAdmissionNow(player: Player): Boolean? {
        val target = synchronized(lock) { engine }
        return target?.viewerAdmission(player)
    }

    override fun applyPersonalVisualNow(player: Player, state: NpcViewerState) {
        val target =
            synchronized(lock) {
                check(!stopping)
                checkNotNull(engine)
            }
        target.applyViewerState(player, state)
    }

    override fun submitPresentation(action: (NpcRenderEngine) -> Unit) = submit(action)

    override fun applyTransform(transform: NpcRenderTransform) = submitAsync {
        val target = it
        target.applyTransform(transform).thenCompose {
            val owner = synchronized(lock) { presentations }
            owner?.followTransform() ?: CompletableFuture.completedFuture<Void>(null)
        }
    }

    override fun canStartClip(clipId: String): Boolean =
        synchronized(lock) {
            !stopping && engine?.canStartClip(clipId) == true
        }

    override fun reserveClip(clipId: String): NpcClipReservation? =
        synchronized(lock) {
            if (stopping) null
            else
                engine?.reserveClip(clipId) { clip, time ->
                    submit { it.playReservedClip(clip, time) }
                }
        }

    override fun playClip(clipId: String, elapsedMillis: Long) = submit {
        require(elapsedMillis >= 0)
        it.playClip(clipId, elapsedMillis)
    }

    override fun stopClip(): CompletionStage<Void> = submit { it.stopClip() }

    override fun stopClipIfPlaying(clipId: String): CompletionStage<Void> = submit {
        it.stopClipIfPlaying(clipId)
    }

    override fun advanceClipTo(elapsedMillis: Long) = submit {
        require(elapsedMillis >= 0)
        it.advanceClipTo(elapsedMillis)
    }

    override fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double) = submit {
        require(yawDegrees.isFinite() && pitchDegrees.isFinite())
        it.applyHeadLook(yawDegrees, pitchDegrees)
    }

    override fun applyViewerState(player: Player, state: NpcViewerState) = submitAsync {
        require(player.isOnline && player.instance === request.instance)
        it.applyViewerState(player, state)
        val owner = synchronized(lock) { presentations }
        owner?.followViewerState(player) ?: CompletableFuture.completedFuture<Void>(null)
    }

    override fun clearViewerState(player: Player) = submitAsync {
        it.clearViewerState(player)
        val owner = synchronized(lock) { presentations }
        owner?.followViewerState(player) ?: CompletableFuture.completedFuture<Void>(null)
    }

    override fun snapshot(): NpcAnchorSnapshot =
        synchronized(lock) { checkNotNull(engine).snapshot() }
}
