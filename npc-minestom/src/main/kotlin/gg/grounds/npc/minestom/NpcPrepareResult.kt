package gg.grounds.npc.minestom

import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

sealed interface NpcPrepareResult {
    data class Ready(val handle: NpcHandle) : NpcPrepareResult

    data class Rejected(val problems: List<NpcProblem>) : NpcPrepareResult
}
