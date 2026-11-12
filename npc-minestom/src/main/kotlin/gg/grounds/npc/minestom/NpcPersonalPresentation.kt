package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcProblem
import gg.grounds.npc.core.NpcVector3
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/** One concrete player's private visual lease; no caller-owned collider or world position. */
internal class NpcPersonalPresentation(
    private val owner: NpcPresentationOwner,
    val options: NpcPresentationOptions,
) : NpcPresentationStart, NpcPresentationHandle {
    private val lock = Any()
    private val ready = CompletableFuture<NpcPresentationResult>()
    val closed = CompletableFuture<Void>()
    private val closing = AtomicBoolean()
    @Volatile private var phase = NpcPresentationPhase.PREPARING
    private var preparation: Task? = null
    private var constructing = false
    private var constructionDone = false
    private var copy: NpcPresentationCopy? = null
    private var cleanupStarted = false
    private val playback = NpcPersonalPlayback(owner.request.model, options)
    private var sampling: Task? = null
    private var inFlightSounds = 0
    private var cleanupFinished = false
    private var cleanupFailure: Throwable? = null
    private var runtimeFailure: Throwable? = null
    private var completionStarted = false
    private var lastFrame = NpcSampleFrame(null, 0.0, NpcVector3.ZERO)

    fun begin() {
        try {
            synchronized(lock) {
                if (closing.get()) {
                    constructionDone = true
                    return@synchronized
                }
                preparation =
                    owner.request.instance
                        .scheduler()
                        .buildTask {
                            val run =
                                synchronized(lock) {
                                    preparation = null
                                    if (closing.get()) {
                                        constructionDone = true
                                        false
                                    } else {
                                        constructing = true
                                        true
                                    }
                                }
                            if (run) construct() else finishClose()
                        }
                        .delay(TaskSchedule.tick(1))
                        .schedule()
            }
        } catch (problem: Throwable) {
            synchronized(lock) { constructionDone = true }
            reject(problem)
        }
        if (closing.get()) finishClose()
    }

    private fun construct() {
        try {
            val child = owner.makeCopy(options, NpcPublicationGuard(::handover)) { live() }
            synchronized(lock) {
                copy = child
                constructing = false
                constructionDone = true
            }
            child.start.ready().whenComplete { prepared, problem ->
                when {
                    problem != null -> reject(problem)
                    closing.get() -> finishClose()
                    prepared is NpcPrepareResult.Ready -> {
                        val accepted =
                            synchronized(lock) {
                                if (closing.get()) false
                                else {
                                    phase = NpcPresentationPhase.READY_HIDDEN
                                    true
                                }
                            }
                        if (accepted) ready.complete(NpcPresentationResult.Ready(this))
                        else finishClose()
                    }
                    else ->
                        reject(
                            IllegalStateException("Private NPC preparation was rejected: $prepared")
                        )
                }
            }
            if (closing.get()) finishClose()
        } catch (problem: Throwable) {
            synchronized(lock) {
                constructing = false
                constructionDone = true
            }
            reject(problem)
        }
    }

    private fun live() = !closing.get() && owner.valid(options.player)

    private fun handover(operation: Runnable): Boolean {
        if (!live()) return false
        if (phase == NpcPresentationPhase.ACTIVE) return true
        var activated = false
        owner.runIfCurrent(
            Runnable {
                options.publicationGuard.runIfCurrent(
                    Runnable {
                        if (!live()) return@Runnable
                        val visual = owner.engine.viewerStateFor(options.player)
                        val renderer =
                            synchronized(lock) { copy }
                                ?: throw IllegalStateException("Private renderer unavailable")
                        renderer.control.applyPersonalVisualNow(options.player, visual)
                        renderNow()
                        if (!owner.engine.suppressViewer(options.player, this) { live() })
                            return@Runnable
                        operation.run()
                        val entered =
                            synchronized(lock) {
                                if (closing.get()) false
                                else {
                                    phase = NpcPresentationPhase.ACTIVE
                                    true
                                }
                            }
                        if (entered) {
                            startSampling()
                            activated = live()
                        }
                    }
                )
            }
        )
        return activated
    }

    override fun publish(): CompletionStage<Void> {
        if (!live()) return failed("Personal presentation is unavailable")
        if (phase == NpcPresentationPhase.ACTIVE) return CompletableFuture.completedFuture(null)
        if (phase != NpcPresentationPhase.READY_HIDDEN)
            return failed("Personal presentation is not ready")
        val child = synchronized(lock) { copy } ?: return failed("Private renderer is unavailable")
        val result = CompletableFuture<Void>()
        child.handle.publish().whenComplete { _, problem ->
            if (problem == null && live()) result.complete(null)
            else
                cancel().whenComplete { _, cleanup ->
                    val failure = problem ?: CancellationException("Personal publication cancelled")
                    if (cleanup != null && cleanup !== failure) failure.addSuppressed(cleanup)
                    result.completeExceptionally(failure)
                }
        }
        return result.minimalCompletionStage()
    }

    private fun command(afterRender: () -> Unit = {}, action: () -> Unit): CompletionStage<Void> {
        if (phase != NpcPresentationPhase.ACTIVE || !live())
            return failed("Personal presentation is not active")
        val child = synchronized(lock) { copy } ?: return failed("Private renderer is unavailable")
        return child.control.submitPresentation {
            check(phase == NpcPresentationPhase.ACTIVE && live()) {
                "Personal presentation is not active"
            }
            action()
            renderNow()
            afterRender()
        }
    }

    private fun startSampling() {
        synchronized(lock) {
            if (closing.get() || sampling != null) return
            sampling =
                owner.request.instance
                    .scheduler()
                    .buildTask {
                        if (!live()) cancel()
                        else
                            try {
                                renderNow()
                            } catch (problem: Throwable) {
                                synchronized(lock) {
                                    if (runtimeFailure == null) runtimeFailure = problem
                                }
                                cancel()
                            }
                    }
                    .delay(TaskSchedule.tick(1))
                    .repeat(TaskSchedule.tick((options.sampleIntervalMillis / 50).toInt()))
                    .schedule()
        }
    }

    private fun renderNow() {
        val common = owner.engine.sampleFrame()
        val frame = playback.compose(common)
        val child =
            synchronized(lock) { copy }
                ?: throw IllegalStateException("Private renderer is unavailable")
        child.control.renderPresentationNow(frame)
        synchronized(lock) { lastFrame = frame }
    }

    private fun playSound(clipId: String) {
        val sound = owner.request.model.sounds[clipId] ?: return
        val origin = owner.engine.snapshot().root.position
        if (!live()) return
        val accepted =
            synchronized(lock) {
                if (closing.get() || phase != NpcPresentationPhase.ACTIVE) false
                else {
                    inFlightSounds++
                    true
                }
            }
        if (!accepted) return
        try {
            if (live()) options.sounds.playFor(options.player, origin, sound)
        } finally {
            synchronized(lock) { inFlightSounds-- }
            completeClose()
        }
    }

    override fun playClip(clipId: String) =
        command(afterRender = { playSound(clipId) }) { playback.play(clipId) }

    override fun stopClip() = command { playback.stop() }

    override fun followSharedClip() = command { playback.followClip() }

    override fun applyHeadLook(yaw: Double, pitch: Double) = command { playback.head(yaw, pitch) }

    override fun followSharedHeadLook() = command { playback.followHead() }

    override fun snapshot(): NpcPresentationSnapshot {
        val anchor = owner.engine.snapshot()
        val channels = playback.channels()
        return synchronized(lock) {
            NpcPresentationSnapshot(
                anchor,
                phase,
                channels.first,
                channels.second,
                lastFrame.clipId,
                lastFrame.seconds,
            )
        }
    }

    override fun ready(): CompletionStage<NpcPresentationResult> = ready.minimalCompletionStage()

    override fun closed(): CompletionStage<Void> = closed.minimalCompletionStage()

    override fun closeAsync() = cancel()

    override fun cancel(): CompletionStage<Void> {
        closing.set(true)
        synchronized(lock) {
            if (phase != NpcPresentationPhase.CLOSED) phase = NpcPresentationPhase.CLOSING
            preparation?.cancel()
            preparation = null
            sampling?.cancel()
            sampling = null
            if (!constructing && copy == null) constructionDone = true
        }
        ready.complete(
            NpcPresentationResult.Rejected(
                listOf(
                    NpcProblem("NPC_PRESENTATION_CANCELLED", "$", "Personal presentation cancelled")
                )
            )
        )
        finishClose()
        return closed.minimalCompletionStage()
    }

    private fun reject(problem: Throwable) {
        ready.complete(
            NpcPresentationResult.Rejected(
                listOf(
                    NpcProblem(
                        "NPC_PRESENTATION_PREPARE_FAILED",
                        "$",
                        problem.message ?: "Private preparation failed",
                    )
                )
            )
        )
        cancel()
    }

    private fun finishClose() {
        val child =
            synchronized(lock) {
                if (!closing.get() || !constructionDone || cleanupStarted) return
                cleanupStarted = true
                copy
            }
        val cleanup =
            try {
                child?.start?.cancel() ?: CompletableFuture.completedFuture(null)
            } catch (problem: Throwable) {
                CompletableFuture.failedFuture<Void>(problem)
            }
        cleanup.whenComplete { _, failure ->
            synchronized(lock) {
                cleanupFailure = failure
                cleanupFinished = true
            }
            completeClose()
        }
    }

    private fun completeClose() {
        val failure =
            synchronized(lock) {
                if (!cleanupFinished || inFlightSounds != 0 || completionStarted) return
                completionStarted = true
                val primary = cleanupFailure ?: runtimeFailure
                if (
                    cleanupFailure != null &&
                        runtimeFailure != null &&
                        cleanupFailure !== runtimeFailure
                )
                    cleanupFailure!!.addSuppressed(runtimeFailure!!)
                primary
            }
        val problem =
            try {
                owner.release(this, failure)
            } catch (error: Throwable) {
                error
            }
        phase = NpcPresentationPhase.CLOSED
        if (problem == null) closed.complete(null) else closed.completeExceptionally(problem)
    }

    private fun failed(message: String): CompletionStage<Void> =
        CompletableFuture.failedFuture(IllegalStateException(message))
}
