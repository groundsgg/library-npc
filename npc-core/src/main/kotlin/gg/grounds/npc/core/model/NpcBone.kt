package gg.grounds.npc.core.model

import gg.grounds.npc.core.math.NpcVector3

/** A bone in parent-before-child order. Origin is model-local; rotation is XYZ degrees. A null parent denotes a root. */
data class NpcBone
@JvmOverloads
constructor(
    val id: String,
    val parent: String?,
    val origin: NpcVector3,
    val rotation: NpcVector3,
    val name: String = id,
)
