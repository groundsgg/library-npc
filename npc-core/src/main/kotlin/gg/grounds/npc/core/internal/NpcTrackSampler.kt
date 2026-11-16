package gg.grounds.npc.core.internal

import gg.grounds.npc.core.animation.NpcInterpolation
import gg.grounds.npc.core.animation.NpcKeyframe
import gg.grounds.npc.core.math.NpcVector3

/**
 * Matches Blockbench Keyframe.getCatmullromLerp / THREE.SplineCurve, including endpoint duplication
 * for one-shots and wrapped tangent keys for looping clips.
 */
internal fun sampleNpcTrack(
    keys: List<NpcKeyframe>,
    time: Double,
    fallback: NpcVector3,
    loop: Boolean = false,
): NpcVector3 {
    if (keys.isEmpty()) return fallback
    if (time < keys.first().timeSeconds) return keys.first().value
    val before = keys.indexOfLast { it.timeSeconds <= time }
    if (before == keys.lastIndex) return keys.last().value
    val a = keys[before]
    val b = keys[before + 1]
    if (a.interpolation == NpcInterpolation.STEP) return a.value
    val t = (time - a.timeSeconds) / (b.timeSeconds - a.timeSeconds)
    if (a.interpolation != NpcInterpolation.CATMULL_ROM && b.interpolation != NpcInterpolation.CATMULL_ROM)
        return a.value * (1 - t) + b.value * t
    val p0 =
        keys.getOrNull(before - 1)?.value
            ?: if (loop && keys.size >= 3) keys[keys.lastIndex - 1].value else a.value
    val p3 =
        keys.getOrNull(before + 2)?.value ?: if (loop && keys.size >= 3) keys[1].value else b.value
    fun c(v0: Double, v1: Double, v2: Double, v3: Double): Double =
        (2 * v1 +
            (-v0 + v2) * t +
            (2 * v0 - 5 * v1 + 4 * v2 - v3) * t * t +
            (-v0 + 3 * v1 - 3 * v2 + v3) * t * t * t) / 2
    return NpcVector3(
        c(p0.x, a.value.x, b.value.x, p3.x),
        c(p0.y, a.value.y, b.value.y, p3.y),
        c(p0.z, a.value.z, b.value.z, p3.z),
    )
}
