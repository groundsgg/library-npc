// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom.internal.rendering

import gg.grounds.npc.minestom.config.NpcRenderConfig
import gg.grounds.npc.minestom.internal.audience.NpcViewerRegistry
import gg.grounds.npc.minestom.internal.lifecycle.NpcEngineHandle
import gg.grounds.npc.minestom.internal.movement.NpcMovementController
import gg.grounds.npc.minestom.internal.playback.NpcSharedPlayback
import gg.grounds.npc.minestom.spi.NpcSoundSink
import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel

import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

internal class NpcRenderEngine(
    private val lock: Any,
    private val model: NpcModel,
    private val displays: List<NpcItemDisplay>,
    private var transform: NpcRenderTransform,
    private val effects: NpcSoundSink,
    private val config: NpcRenderConfig,
    private val audience: NpcRenderAudience = NpcRenderAudience.Shared,
) : NpcEngineHandle {
    private val closedCleanup = java.util.concurrent.CompletableFuture<Void>()

    private data class SoundEmission(
        val instance: Instance,
        val origin: NpcVector3,
        val sound: NpcClipSound,
    )

    private val pendingSounds = ArrayList<SoundEmission>()
    private val renderer = NpcDisplayRenderer(model, displays)
    private var closed = false
    private var published = false
    private val playback = NpcSharedPlayback(model, config, ::hasNearbyPlayer, ::playSound)
    private val elapsed get() = playback.elapsed
    private var headLook = NpcVector3(0.0, 0.0, 0.0)
    private var pendingHeadLook = headLook
    private var sampledClip: String? = null
    private var sampledSeconds = 0.0
    private var externalFrame: NpcSampleFrame? = null


    private val movement = NpcMovementController(lock, displays, { transform }, { next, moved ->
        transform = next
        if (moved || playback.requested == null) advance(elapsed, true)
    }, ::dispatchSounds)
    private val viewers = NpcViewerRegistry(lock, displays, config, audience, { transform }, { closed }, { published }, { elapsed },
        { advance(elapsed, true, false, false) }, ::dispatchSounds)

    fun currentTransform(): NpcRenderTransform = synchronized(lock) { transform }
    fun inheritViewerAdmission(player: Player) = viewers.inheritViewerAdmission(player)
    fun viewerAdmission(player: Player): Boolean? = viewers.viewerAdmission(player)
    fun viewerStateFor(player: Player): NpcViewerState = viewers.viewerStateFor(player)
    fun canPresentTo(player: Player): Boolean = viewers.canPresentTo(player)
    fun suppressViewer(player: Player, lease: Any, current: () -> Boolean): Boolean = viewers.suppressViewer(player, lease, current)
    fun ownsSuppression(player: Player, lease: Any): Boolean = viewers.ownsSuppression(player, lease)
    fun releaseSuppression(player: Player, lease: Any, restore: Boolean, admission: Boolean? = null) = viewers.releaseSuppression(player, lease, restore, admission)
    override fun applyViewerState(player: Player, state: NpcViewerState) = viewers.applyViewerState(player, state)
    override fun clearViewerState(player: Player) = viewers.clearViewerState(player)

    override val headLookOrigin: NpcVector3?
        get() =
            model.headBone?.let { bone ->
                // Imported bones face -Z; the model-facing correction makes Scene forward +Z.
                NpcVector3(-bone.origin.x, bone.origin.y + 0.15, -bone.origin.z)
            }

    override fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double) {
        update {
            if (closed) return
            // Conjugating by the model's Y180 facing correction reverses the pitch axis.
            val next = NpcVector3(-pitchDegrees, yawDegrees, 0.0)
            if (pendingHeadLook == next) return
            pendingHeadLook = next
            if (playback.requested == null) advance(elapsed, true)
        }
    }

    init {
        render(null, 0.0)
        viewers.refreshViewers()
    }

    private val visibilityTask =
        displays
            .first()
            .instance
            .scheduler()
            .buildTask { update { if (!closed) viewers.refreshViewers() } }
            .repeat(TaskSchedule.tick(4))
            .schedule()

    fun publishVisible() {
        update {
            if (!closed) {
                published = true
                try {
                    viewers.refreshViewers()
                } finally {
                    viewers.clearInheritedAdmission()
                }
            }
        }
    }

    fun sampleFrame(): NpcSampleFrame =
        synchronized(lock) {
            NpcSampleFrame(sampledClip, sampledSeconds, headLook)
        }

    fun refreshAudience() = update { if (!closed) viewers.refreshViewers() }

    fun renderFrame(frame: NpcSampleFrame) = update {
        if (closed) return
        require(frame.seconds.isFinite() && frame.seconds >= 0)
        require(frame.clipId == null || frame.clipId in model.animations)
        externalFrame = frame
        headLook = frame.headLook
        pendingHeadLook = frame.headLook
        sampledClip = frame.clipId
        sampledSeconds = frame.seconds
        viewers.refreshViewers()
        if (viewers.hasViewers()) {
            render(frame.clipId, frame.seconds)
            viewers.flushViewerUpdates(elapsed, true)
        }
    }

    fun snapshot(): NpcAnchorSnapshot =
        synchronized(lock) { NpcAnchorSnapshot(transform.root, headLookOrigin) }

    fun drainMovements(): java.util.concurrent.CompletionStage<Void> =
        if (synchronized(lock) { closed }) closedCleanup.minimalCompletionStage() else movement.drain()

    override fun applyTransform(transform: NpcRenderTransform): java.util.concurrent.CompletionStage<Void> = movement.applyTransform(transform)

    override fun playClip(animation: String, elapsedMillis: Long) {
        update {
            if (closed) return
            playback.start(animation, elapsedMillis)
            if (elapsedMillis == 0L) playSound(animation)
            advance(elapsedMillis, true)
        }
    }

    override fun stopClipIfPlaying(animation: String?) {
        update {
            if (closed || !playback.stop(animation)) return
            commitHeadLook()
            render(null, 0.0)
            viewers.flushViewerUpdates(elapsed, true)
        }
    }

    override fun advanceClipTo(elapsedMillis: Long) {
        update { if (!closed) advance(elapsedMillis, false) }
    }

    private fun advance(
        time: Long,
        force: Boolean,
        updateViewers: Boolean = true,
        flush: Boolean = true,
    ) {
        val seeking = playback.seek(time)
        if (updateViewers) viewers.refreshViewers()
        val personal = externalFrame
        if (personal != null) {
            headLook = personal.headLook
            sampledClip = personal.clipId
            sampledSeconds = personal.seconds
            if (viewers.hasViewers()) {
                render(personal.clipId, personal.seconds, !flush)
                if (flush) viewers.flushViewerUpdates(time, force || seeking)
            }
            return
        }
        if (!viewers.hasViewers()) {
            commitHeadLook()
            playback.sampleHidden()
            sampledClip = playback.sampledClip
            sampledSeconds = playback.sampledSeconds
            return
        }
        if (!playback.shouldRender(time, force, seeking, viewers.renderInterval)) {
            if (flush) viewers.flushViewerUpdates(time, seeking)
            return
        }
        commitHeadLook()
        playback.sampleVisible()
        val beforeSingleDeadline =
            viewers.deferUpdate(time)
        render(playback.sampledClip, playback.sampledSeconds, !flush || beforeSingleDeadline)
        if (flush) viewers.flushViewerUpdates(time, force || seeking)
    }

    private fun hasNearbyPlayer(): Boolean {
        val anchor = transform.root.position
        return displays.first().instance.players.any { player ->
            val position = player.position
            val dx = position.x() - anchor.x
            val dy = position.y() - anchor.y
            val dz = position.z() - anchor.z
            dx*dx + dy*dy + dz*dz <= config.variationMinDistance*config.variationMinDistance
        }
    }

    private fun playSound(name: String) {
        if (audience !== NpcRenderAudience.Shared) return
        model.sounds[name]?.let {
            pendingSounds += SoundEmission(displays.first().instance, transform.root.position, it)
        }
    }

    private inline fun <T> update(action: () -> T): T {
        try {
            return synchronized(lock) { action() }
        } finally {
            dispatchSounds()
        }
    }

    private fun dispatchSounds() {
        // Nested updates leave delivery to the outermost mutation after its monitor is released.
        if (Thread.holdsLock(lock)) return
        val emissions =
            synchronized(lock) {
                if (closed) {
                    pendingSounds.clear()
                    return
                }
                if (pendingSounds.isEmpty()) return
                pendingSounds.toList().also { pendingSounds.clear() }
            }
        emissions.forEach { effects.playNear(it.instance, it.origin, it.sound) }
    }

    private fun commitHeadLook() {
        if (
            abs(pendingHeadLook.x - headLook.x) >= 2.0 || abs(pendingHeadLook.y - headLook.y) >= 2.0
        )
            headLook = pendingHeadLook
    }

    private fun render(name: String?, seconds: Double, deferUpdates: Boolean = false) {
        val pose = renderer.sample(name, seconds, headLook)
        sampledClip = name
        sampledSeconds = seconds
        renderer.apply(pose, transform, viewers.renderInterval, deferUpdates)
    }

    override fun close() {
        synchronized(lock) {
                if (closed) return
                closed = true
                visibilityTask.cancel()
                pendingSounds.clear()
                viewers.clear()
                Unit
            }
        // Queued owner work needs no further tick; accepted I/O/events must actually settle.
        movement.close().whenComplete { _, movementFailure ->
            var failure = movementFailure
            displays.forEach { display ->
                try {
                    synchronized(lock) {
                        display.viewerState = null
                        display.visualState = null
                        display.clearUpdates()
                    }
                    display.remove()
                } catch (problem: Throwable) {
                    if (failure == null) failure = problem
                    else if (failure !== problem) failure!!.addSuppressed(problem)
                }
            }
            if (failure == null) closedCleanup.complete(null)
            else closedCleanup.completeExceptionally(failure!!)
        }
    }
}

