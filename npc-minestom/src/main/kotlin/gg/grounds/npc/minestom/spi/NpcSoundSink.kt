package gg.grounds.npc.minestom.spi

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import net.minestom.server.instance.Instance

/**
 * Application-owned shared spatial sound delivery. The default sink is silent; personal-only cues
 * use NpcViewerSoundSink instead.
 */
public fun interface NpcSoundSink {
    /**
     * Deliver an explicit shared clip cue near the supplied world origin. Called outside NPC
     * monitors; throwing fails the calling command. Default delivery is silent.
     */
    public fun playNear(instance: Instance, origin: NpcVector3, sound: NpcClipSound)
}
