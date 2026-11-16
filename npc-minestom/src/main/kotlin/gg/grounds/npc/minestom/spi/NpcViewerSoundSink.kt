package gg.grounds.npc.minestom.spi

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import net.minestom.server.entity.Player

fun interface NpcViewerSoundSink {
    fun playFor(player: Player, origin: NpcVector3, sound: NpcClipSound)
}
