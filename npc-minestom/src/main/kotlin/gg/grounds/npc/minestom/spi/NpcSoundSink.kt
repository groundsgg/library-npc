package gg.grounds.npc.minestom.spi

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import net.minestom.server.instance.Instance

fun interface NpcSoundSink {
    fun playNear(instance: Instance, origin: NpcVector3, sound: NpcClipSound)
}
