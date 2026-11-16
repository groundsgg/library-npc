package gg.grounds.npc.minestom

import gg.grounds.npc.core.validation.NpcProblem

sealed interface NpcPrepareResult {
    data class Ready(val handle: NpcHandle) : NpcPrepareResult

    data class Rejected(val problems: List<NpcProblem>) : NpcPrepareResult
}
