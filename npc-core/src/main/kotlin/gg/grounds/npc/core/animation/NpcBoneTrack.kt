package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList

/**
 * Immutable numeric channels for one bone. Each supplied list is copied. Empty position/rotation
 * channels retain the bind pose; empty scale retains unit scale.
 *
 * @property position Immutable ordered translation keys in authored model units.
 * @property rotation Immutable ordered additive XYZ rotation keys in degrees.
 * @property scale Immutable ordered component-scale keys; an empty channel retains unit scale.
 */
public class NpcBoneTrack
@JvmOverloads
constructor(
    position: List<NpcKeyframe> = emptyList(),
    rotation: List<NpcKeyframe> = emptyList(),
    scale: List<NpcKeyframe> = emptyList(),
) {
    /** Immutable ordered translation keys in authored model units. */
    public val position: List<NpcKeyframe> = immutableList(position)
    /** Immutable ordered additive XYZ rotation keys in degrees. */
    public val rotation: List<NpcKeyframe> = immutableList(rotation)
    /** Immutable ordered component-scale keys; an empty channel retains unit scale. */
    public val scale: List<NpcKeyframe> = immutableList(scale)

    internal fun channels(): List<Pair<String, List<NpcKeyframe>>> =
        listOf("position" to position, "rotation" to rotation, "scale" to scale)
}
