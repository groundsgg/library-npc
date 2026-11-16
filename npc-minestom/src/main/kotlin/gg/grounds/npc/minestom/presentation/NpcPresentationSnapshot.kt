package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot

data class NpcPresentationSnapshot(
    val anchor: NpcAnchorSnapshot,
    val phase: NpcPresentationPhase,
    val clipMode: NpcPresentationChannel,
    val headMode: NpcPresentationChannel,
    val clipId: String?,
    val clipSeconds: Double,
)
