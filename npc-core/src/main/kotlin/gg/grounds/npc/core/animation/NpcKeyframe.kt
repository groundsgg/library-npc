package gg.grounds.npc.core.animation

import gg.grounds.npc.core.math.NpcVector3

/** An authored channel value at nonnegative seconds; model validation checks ordering and clip bounds. */
data class NpcKeyframe(val timeSeconds: Double, val value: NpcVector3, val interpolation: NpcInterpolation)
