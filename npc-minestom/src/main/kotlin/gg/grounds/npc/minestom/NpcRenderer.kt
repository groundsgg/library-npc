package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.internal.lifecycle.PreparedNpc
import gg.grounds.npc.minestom.presentation.NpcPresentationLimits

object NpcRenderer {
    @JvmStatic
    fun prepare(request: NpcSpawnRequest): NpcStart = PreparedNpc(request).also { it.begin() }

    @JvmStatic
    fun prepare(request: NpcSpawnRequest, limits: NpcPresentationLimits): NpcStart =
        PreparedNpc(request, limits).also { it.begin() }
}
