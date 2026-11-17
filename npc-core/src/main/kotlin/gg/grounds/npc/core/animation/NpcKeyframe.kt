package gg.grounds.npc.core.animation

import gg.grounds.npc.core.math.NpcVector3

/**
 * One numeric channel value at an authored time in seconds. The containing model validates a finite
 * nonnegative time within its clip and ordered key lists.
 *
 * @property timeSeconds Nonnegative finite key time in seconds within the containing clip.
 * @property value Authored numeric channel vector.
 * @property interpolation Typed interpolation policy for adjacent keys.
 */
public data class NpcKeyframe(
    /** Nonnegative finite key time in seconds within the containing clip. */
    val timeSeconds: Double,
    /** Authored numeric channel vector. */
    val value: NpcVector3,
    /** Typed interpolation policy for adjacent keys. */
    val interpolation: NpcInterpolation,
)
