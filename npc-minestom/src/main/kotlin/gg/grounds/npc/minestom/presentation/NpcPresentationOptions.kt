package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.minestom.spi.NpcMonotonicClock
import gg.grounds.npc.minestom.spi.NpcPublicationGuard
import gg.grounds.npc.minestom.spi.NpcViewerSoundSink
import net.minestom.server.entity.Player

class NpcPresentationOptions private constructor(builder: Builder) {
    val player: Player = builder.player
    val publicationGuard: NpcPublicationGuard = builder.publicationGuard
    val sounds: NpcViewerSoundSink = builder.sounds
    val idleClip: String? = builder.idleClip
    val sampleIntervalMillis: Long = builder.sampleIntervalMillis
    val clock: NpcMonotonicClock = builder.clock

    init {
        require(idleClip == null || idleClip.isNotBlank()) { "idleClip must be null or nonblank" }
        require(sampleIntervalMillis in 50..10_000 && sampleIntervalMillis % 50 == 0L) {
            "sampleIntervalMillis must be 50..10000 in multiples of 50"
        }
    }

    fun toBuilder(): Builder =
        builder(player)
            .publicationGuard(publicationGuard)
            .sounds(sounds)
            .idleClip(idleClip)
            .sampleIntervalMillis(sampleIntervalMillis)
            .clock(clock)

    companion object {
        @JvmStatic fun builder(player: Player): Builder = Builder(player)
    }

    /**
     * Mutable construction helper; confine a builder to one thread. Built options are immutable.
     */
    class Builder internal constructor(internal var player: Player) {
        internal var publicationGuard: NpcPublicationGuard = NpcPublicationGuard {
            it.run()
            true
        }
        internal var sounds: NpcViewerSoundSink = NpcViewerSoundSink { _, _, _ -> }
        internal var idleClip: String? = null
        internal var sampleIntervalMillis: Long = 50
        internal var clock: NpcMonotonicClock = NpcMonotonicClock(System::nanoTime)

        fun player(value: Player): Builder = apply { player = value }

        fun publicationGuard(value: NpcPublicationGuard): Builder = apply {
            publicationGuard = value
        }

        fun sounds(value: NpcViewerSoundSink): Builder = apply { sounds = value }

        fun idleClip(value: String?): Builder = apply { idleClip = value }

        fun sampleIntervalMillis(value: Long): Builder = apply { sampleIntervalMillis = value }

        fun clock(value: NpcMonotonicClock): Builder = apply { clock = value }

        fun build(): NpcPresentationOptions = NpcPresentationOptions(this)
    }
}
