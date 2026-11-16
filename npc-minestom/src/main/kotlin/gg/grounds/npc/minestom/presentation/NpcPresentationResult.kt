package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.core.validation.NpcProblem

sealed interface NpcPresentationResult {
    data class Ready(val handle: NpcPresentationHandle) : NpcPresentationResult

    data class Rejected(val problems: List<NpcProblem>) : NpcPresentationResult
}
