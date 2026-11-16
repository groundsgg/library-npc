package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList

/** Immutable numeric channels for one bone. Inputs are defensively copied; empty channels retain the bind value. */
class NpcBoneTrack @JvmOverloads constructor(
    position: List<NpcKeyframe> = emptyList(),
    rotation: List<NpcKeyframe> = emptyList(),
    scale: List<NpcKeyframe> = emptyList(),
) {
    /** Translation keys in authored model units. */
    val position: List<NpcKeyframe> = immutableList(position)
    /** Additive XYZ rotation keys in degrees. */
    val rotation: List<NpcKeyframe> = immutableList(rotation)
    /** Component scale keys; an absent channel samples the identity scale. */
    val scale: List<NpcKeyframe> = immutableList(scale)
    internal fun channels(): List<Pair<String, List<NpcKeyframe>>> = listOf("position" to position, "rotation" to rotation, "scale" to scale)
}
