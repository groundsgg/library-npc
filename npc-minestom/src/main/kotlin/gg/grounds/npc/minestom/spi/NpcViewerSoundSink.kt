package gg.grounds.npc.minestom.spi

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import net.minestom.server.entity.Player

/**
 * Application-owned sound delivery exclusively to one concrete Player connection. There is no
 * shared/spatial fallback; default delivery is silent.
 */
public fun interface NpcViewerSoundSink {
    /**
     * Deliver one explicit private clip cue exclusively to this concrete Player. Do not fall back
     * to shared/spatial delivery. Called outside NPC monitors; callback completion is included in
     * cleanup draining.
     */
    public fun playFor(player: Player, origin: NpcVector3, sound: NpcClipSound)
}
