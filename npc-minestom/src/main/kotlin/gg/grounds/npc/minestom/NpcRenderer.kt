package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.internal.lifecycle.PreparedNpc
import gg.grounds.npc.minestom.presentation.NpcPresentationLimits

import gg.grounds.npc.core.validation.NpcProblem

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.timer.TaskSchedule

object NpcRenderer {
    @JvmStatic
    fun prepare(request: NpcSpawnRequest): NpcStart = PreparedNpc(request).also { it.begin() }

    @JvmStatic
    fun prepare(request: NpcSpawnRequest, limits: NpcPresentationLimits): NpcStart =
        PreparedNpc(request, limits).also { it.begin() }
}

