package gg.grounds.npc.core.animation

/** Interpolation between numeric keyframes; no authored expression is evaluated. */
public enum class NpcInterpolation {
    /** Interpolate linearly between adjacent keys. */
    LINEAR,
    /** Keep the preceding key until the next key is reached. */
    STEP,
    /** Catmull–Rom interpolation, including wrapped loop tangents. */
    CATMULL_ROM,
}
