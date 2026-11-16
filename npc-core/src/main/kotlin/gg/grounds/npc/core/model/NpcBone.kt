package gg.grounds.npc.core.model

import gg.grounds.npc.core.math.NpcVector3

/**
 * One authored bone. Parents must precede their children in the containing model. Null parent
 * denotes a root; rotations use XYZ degrees.
 *
 * @property id Unique local bone ID, 1..128 characters.
 * @property parent Local ID of an earlier bone, or null for a root.
 * @property origin Pivot in authored model coordinates.
 * @property rotation XYZ rotation in degrees, or ordered additive rotation keys for a bone track.
 * @property name Authored bone label; a unique case-insensitive head label determines the look
 *   subtree.
 */
public data class NpcBone
@JvmOverloads
constructor(
    /** Namespaced model ID or unique local bone ID, according to the containing type. */
    val id: String,
    /** Local ID of an earlier bone, or null for a root. */
    val parent: String?,
    /** Pivot in authored model coordinates. */
    val origin: NpcVector3,
    /** XYZ rotation in degrees, or ordered additive rotation keys for a bone track. */
    val rotation: NpcVector3,
    /** Authored bone label; a unique case-insensitive head label determines the look subtree. */
    val name: String = id,
)
