package gg.grounds.npc.minestom.internal.playback

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.minestom.NpcClipReservation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.ceil

/** Owns shared audio admission; no scheduler or external callback runs under its monitor. */
internal class NpcClipPlaybackGate(
    private val model: NpcModel,
    private val enabled: Boolean,
    private val clock: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private var closed = false
    private var reservation: Any? = null
    private var holdStarted = 0L
    private var holdNanos = 0L

    fun canStart(clip: String): Boolean {
        val now = clock()
        return synchronized(lock) { canStartLocked(clip, now) }
    }

    private fun canStartLocked(clip: String, now: Long, restoring: Boolean = false): Boolean {
        val animation = model.animations[clip] ?: return false
        return !closed &&
            reservation == null &&
            (!enabled ||
                restoring ||
                animation.loop == NpcLoopMode.LOOP && clip !in model.sounds ||
                holdNanos == 0L ||
                now - holdStarted >= holdNanos)
    }

    private fun holdMillis(clip: String, elapsedMillis: Long): Long {
        if (!enabled) return 0
        val cue = model.sounds[clip] ?: return 0
        val millis =
            maxOf(
                model.animations.getValue(clip).lengthSeconds * 1000,
                cue.durationMillis / minOf(cue.pitch, 1.0),
            )
        val bounded =
            ceil(millis).coerceAtMost((Long.MAX_VALUE / 4 / 1_000_000 - 200).toDouble()).toLong()
        return ((bounded + 49) / 50 * 50 + 100 - elapsedMillis).coerceAtLeast(0)
    }

    private fun holdLocked(now: Long, remaining: Long) {
        if (remaining == 0L) return
        val previous =
            if (holdNanos == 0L) 0 else (holdNanos - (now - holdStarted)).coerceAtLeast(0)
        holdStarted = now
        holdNanos = maxOf(previous, remaining * 1_000_000)
    }

    fun started(clip: String, elapsedMillis: Long) {
        val remaining = holdMillis(clip, elapsedMillis)
        val now = clock()
        synchronized(lock) { holdLocked(now, remaining) }
    }

    fun tryStart(clip: String, elapsedMillis: Long): Boolean {
        val remaining = holdMillis(clip, elapsedMillis)
        val now = clock()
        return synchronized(lock) {
            if (!canStartLocked(clip, now, elapsedMillis > 0)) false
            else {
                holdLocked(now, remaining)
                true
            }
        }
    }

    fun reserve(clip: String, start: (String, Long) -> CompletionStage<Void>): NpcClipReservation? {
        val now = clock()
        val token = Any()
        synchronized(lock) {
            if (!canStartLocked(clip, now)) return null
            reservation = token
        }
        return object : NpcClipReservation {
            private var used = false
            private var released = false

            override fun start(elapsedMillis: Long): CompletionStage<Void> {
                require(elapsedMillis >= 0)
                synchronized(lock) {
                    check(!closed && !released && !used && reservation === token)
                    used = true
                }
                val result =
                    try {
                        start(clip, elapsedMillis)
                    } catch (error: Throwable) {
                        CompletableFuture.failedFuture<Void>(error)
                    }
                result.whenComplete { _, _ ->
                    synchronized(lock) {
                        released = true
                        if (reservation === token) reservation = null
                    }
                }
                return result
            }

            override fun close() {
                synchronized(lock) {
                    if (!used) {
                        released = true
                        if (reservation === token) reservation = null
                    }
                }
            }
        }
    }

    fun close() =
        synchronized(lock) {
            closed = true
            reservation = null
        }
}
