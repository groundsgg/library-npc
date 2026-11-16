package gg.grounds.npc.minestom.internal.playback

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.minestom.internal.rendering.NpcSampleFrame
import gg.grounds.npc.minestom.presentation.NpcPresentationChannel
import gg.grounds.npc.minestom.presentation.NpcPresentationOptions

/** Independent clip/head channels. No entity access and no clock callback under a state lock. */
internal class NpcPersonalPlayback(
    private val model: NpcModel,
    private val options: NpcPresentationOptions,
) {
    private val lock = Any()
    private var clipMode = NpcPresentationChannel.FOLLOW_SHARED
    private var headMode = NpcPresentationChannel.FOLLOW_SHARED
    private var clipId: String? = null
    private var startedNanos = 0L
    private var elapsedNanos = 0L
    private var initialFrame = false
    private var head = NpcVector3.ZERO

    fun play(clip: String) {
        require(clip in model.animations) { "Unknown NPC clip $clip" }
        val now = options.clock.nanoTime()
        synchronized(lock) {
            clipId = clip
            startedNanos = now
            elapsedNanos = 0
            initialFrame = true
            clipMode = NpcPresentationChannel.PRIVATE
        }
    }

    fun stop() {
        val now = if (options.idleClip == null) 0 else options.clock.nanoTime()
        synchronized(lock) {
            clipId = options.idleClip
            startedNanos = now
            elapsedNanos = 0
            initialFrame = true
            clipMode = NpcPresentationChannel.PRIVATE
        }
    }

    fun followClip() = synchronized(lock) { clipMode = NpcPresentationChannel.FOLLOW_SHARED }

    fun followHead() = synchronized(lock) { headMode = NpcPresentationChannel.FOLLOW_SHARED }

    fun head(yaw: Double, pitch: Double) {
        require(yaw.isFinite() && pitch.isFinite())
        synchronized(lock) {
            head = NpcVector3(-pitch, yaw, 0.0)
            headMode = NpcPresentationChannel.PRIVATE
        }
    }

    fun channels(): Pair<NpcPresentationChannel, NpcPresentationChannel> =
        synchronized(lock) { clipMode to headMode }

    fun compose(common: NpcSampleFrame): NpcSampleFrame {
        val needsClock =
            synchronized(lock) {
                clipMode == NpcPresentationChannel.PRIVATE && clipId != null && !initialFrame
            }
        val now = if (needsClock) options.clock.nanoTime() else 0L
        return synchronized(lock) {
            var selected = common.clipId
            var seconds = common.seconds
            if (clipMode == NpcPresentationChannel.PRIVATE) {
                selected = clipId
                if (selected == null) seconds = 0.0
                else {
                    if (!initialFrame)
                        elapsedNanos = maxOf(elapsedNanos, maxOf(0L, now - startedNanos))
                    seconds = elapsedNanos / 1_000_000_000.0
                    val clip = model.animations.getValue(selected)
                    if (
                        clip.loop == NpcLoopMode.ONCE &&
                            selected != options.idleClip &&
                            seconds >= clip.lengthSeconds
                    ) {
                        selected = options.idleClip
                        seconds = if (selected == null) 0.0 else seconds - clip.lengthSeconds
                    }
                }
            }
            initialFrame = false
            NpcSampleFrame(
                selected,
                seconds,
                if (headMode == NpcPresentationChannel.PRIVATE) head else common.headLook,
            )
        }
    }
}
