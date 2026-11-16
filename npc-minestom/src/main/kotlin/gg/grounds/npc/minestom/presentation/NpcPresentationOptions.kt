package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.minestom.spi.NpcMonotonicClock
import gg.grounds.npc.minestom.spi.NpcPublicationGuard
import gg.grounds.npc.minestom.spi.NpcViewerSoundSink
import net.minestom.server.entity.Player

/**
 * Immutable options for one concrete viewer connection. A personal presentation starts hidden and
 * follows shared clip/head channels until explicitly overridden. Its own monotonic clock samples
 * every 50 ms by default.
 *
 * @property player Concrete online Player connection in the parent instance; a reconnect inherits
 *   nothing.
 * @property publicationGuard Synchronous atomic host generation check; the default runs the
 *   operation immediately.
 * @property sounds Exclusive cue delivery to this concrete viewer; defaults to silent, with no
 *   spatial fallback.
 * @property idleClip Existing clip ID for idle playback, or null for rest. Existence is checked
 *   when a model is available.
 * @property sampleIntervalMillis Personal sample interval, 50..10000 ms in steps of 50; default 50.
 * @property clock Monotonic nanosecond source; defaults to System.nanoTime and is never a world
 *   clock.
 */
public class NpcPresentationOptions private constructor(builder: Builder) {
    /** Concrete online Player connection in the parent instance; a reconnect inherits nothing. */
    public val player: Player = builder.player
    /** Synchronous atomic host generation check; the default runs the operation immediately. */
    public val publicationGuard: NpcPublicationGuard = builder.publicationGuard
    /**
     * Exclusive cue delivery to this concrete viewer; defaults to silent, with no spatial fallback.
     */
    public val sounds: NpcViewerSoundSink = builder.sounds
    /**
     * Existing clip ID for idle playback, or null for rest. Existence is checked when a model is
     * available.
     */
    public val idleClip: String? = builder.idleClip
    /** Personal sample interval, 50..10000 ms in steps of 50; default 50. */
    public val sampleIntervalMillis: Long = builder.sampleIntervalMillis
    /** Monotonic nanosecond source; defaults to System.nanoTime and is never a world clock. */
    public val clock: NpcMonotonicClock = builder.clock

    init {
        require(idleClip == null || idleClip.isNotBlank()) { "idleClip must be null or nonblank" }
        require(sampleIntervalMillis in 50..10_000 && sampleIntervalMillis % 50 == 0L) {
            "sampleIntervalMillis must be 50..10000 in multiples of 50"
        }
    }

    /**
     * Copy these immutable options into an independent mutable builder. Later changes cannot mutate
     * this instance.
     */
    public fun toBuilder(): Builder =
        builder(player)
            .publicationGuard(publicationGuard)
            .sounds(sounds)
            .idleClip(idleClip)
            .sampleIntervalMillis(sampleIntervalMillis)
            .clock(clock)

    /**
     * Shared factories and constants; Java entrypoints are exposed with their documented static
     * annotations.
     */
    public companion object {
        /** Create a thread-confined builder; defaults are validated when build is called. */
        @JvmStatic public fun builder(player: Player): Builder = Builder(player)
    }

    /**
     * Mutable construction helper; confine a builder to one thread. Built options are immutable.
     */
    public class Builder internal constructor(internal var player: Player) {
        internal var publicationGuard: NpcPublicationGuard = NpcPublicationGuard {
            it.run()
            true
        }
        internal var sounds: NpcViewerSoundSink = NpcViewerSoundSink { _, _, _ -> }
        internal var idleClip: String? = null
        internal var sampleIntervalMillis: Long = 50
        internal var clock: NpcMonotonicClock = NpcMonotonicClock(System::nanoTime)

        /**
         * Configure concrete online Player connection in the parent instance; a reconnect inherits
         * nothing. Validation occurs at build; returns this builder for chaining.
         */
        public fun player(value: Player): Builder = apply { player = value }

        /**
         * Configure synchronous atomic host generation check; the default runs the operation
         * immediately. Validation occurs at build; returns this builder for chaining.
         */
        public fun publicationGuard(value: NpcPublicationGuard): Builder = apply {
            publicationGuard = value
        }

        /**
         * Exclusive cue callback for this concrete viewer connection; defaults to silent without
         * shared fallback. Returns this builder for chaining.
         */
        public fun sounds(value: NpcViewerSoundSink): Builder = apply { sounds = value }

        /**
         * Configure existing clip ID for idle playback, or null for rest. Existence is checked when
         * a model is available. Validation occurs at build; returns this builder for chaining.
         */
        public fun idleClip(value: String?): Builder = apply { idleClip = value }

        /**
         * Configure personal sample interval, 50..10000 ms in steps of 50; default 50. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun sampleIntervalMillis(value: Long): Builder = apply {
            sampleIntervalMillis = value
        }

        /**
         * Configure monotonic nanosecond source; defaults to System.nanoTime and is never a world
         * clock. Validation occurs at build; returns this builder for chaining.
         */
        public fun clock(value: NpcMonotonicClock): Builder = apply { clock = value }

        /**
         * Validate the configured values and create independent immutable options. Invalid ranges
         * or relationships throw IllegalArgumentException with the property name.
         */
        public fun build(): NpcPresentationOptions = NpcPresentationOptions(this)
    }
}
