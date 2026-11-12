package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcModel
import gg.grounds.npc.core.NpcVector3

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
    private var head = NpcVector3.ZERO

    fun play(clip: String) {
        require(clip in model.animations) { "Unknown NPC clip $clip" }
        val now = options.clock.nanoTime()
        synchronized(lock) {
            clipId = clip
            startedNanos = now
            elapsedNanos = 0
            clipMode = NpcPresentationChannel.PRIVATE
        }
    }

    fun stop() {
        val now = if (options.idleClip == null) 0 else options.clock.nanoTime()
        synchronized(lock) {
            clipId = options.idleClip
            startedNanos = now
            elapsedNanos = 0
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
            synchronized(lock) { clipMode == NpcPresentationChannel.PRIVATE && clipId != null }
        val now = if (needsClock) options.clock.nanoTime() else 0L
        return synchronized(lock) {
            var selected = common.clipId
            var seconds = common.seconds
            if (clipMode == NpcPresentationChannel.PRIVATE) {
                selected = clipId
                if (selected == null) seconds = 0.0
                else {
                    elapsedNanos = maxOf(elapsedNanos, maxOf(0L, now - startedNanos))
                    seconds = elapsedNanos / 1_000_000_000.0
                    val clip = model.animations.getValue(selected)
                    if (
                        clip.loop == "once" &&
                            selected != options.idleClip &&
                            seconds >= clip.length
                    ) {
                        selected = options.idleClip
                        seconds = if (selected == null) 0.0 else seconds - clip.length
                    }
                }
            }
            NpcSampleFrame(
                selected,
                seconds,
                if (headMode == NpcPresentationChannel.PRIVATE) head else common.headLook,
            )
        }
    }
}
