package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.internal.lifecycle.PreparedNpc
import gg.grounds.npc.minestom.presentation.NpcPresentationLimits

/**
 * Entry point for hidden Minestom NPC preparation. The returned start owns cancellation from the
 * moment prepare returns, before any handle is ready.
 */
public object NpcRenderer {
    /**
     * Begin hidden preparation and return cancellation ownership immediately. Retain the start
     * before observing ready; limits cap personal groups, not shared model parts. Preparation
     * rejection is a result, and cleanup remains observable through cancel.
     */
    @JvmStatic
    public fun prepare(request: NpcSpawnRequest): NpcStart =
        PreparedNpc(request).also { it.begin() }

    /**
     * Begin hidden preparation and return cancellation ownership immediately. Retain the start
     * before observing ready; limits cap personal groups, not shared model parts. Preparation
     * rejection is a result, and cleanup remains observable through cancel.
     */
    @JvmStatic
    public fun prepare(request: NpcSpawnRequest, limits: NpcPresentationLimits): NpcStart =
        PreparedNpc(request, limits).also { it.begin() }
}
