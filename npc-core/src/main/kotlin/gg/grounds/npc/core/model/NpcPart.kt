package gg.grounds.npc.core.model

import gg.grounds.npc.core.math.NpcVector3

/** An item-mesh part bound to an optional bone; centres and pivots use authored model coordinates. */
data class NpcPart(
    val bone: String?,
    val model: String,
    val center: NpcVector3,
    val pivot: NpcVector3,
    val rotation: NpcVector3,
    val scale: NpcVector3 = NpcVector3(1.0, 1.0, 1.0),
)
