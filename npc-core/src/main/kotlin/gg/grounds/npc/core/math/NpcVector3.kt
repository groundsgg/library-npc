package gg.grounds.npc.core.math

/** Finite immutable XYZ components in authored model units. Negative zero is canonicalized to zero. */
@ConsistentCopyVisibility
data class NpcVector3
private constructor(
    val x: Double,
    val y: Double,
    val z: Double,
    @Suppress("unused") private val canonical: Unit,
) {
    constructor(
        x: Double,
        y: Double,
        z: Double,
    ) : this(canonicalComponent(x), canonicalComponent(y), canonicalComponent(z), Unit)

    /** Add model-local components without modifying either vector. */
    operator fun plus(other: NpcVector3): NpcVector3 = NpcVector3(x + other.x, y + other.y, z + other.z)
    /** Subtract model-local components without modifying either vector. */
    operator fun minus(other: NpcVector3): NpcVector3 = NpcVector3(x - other.x, y - other.y, z - other.z)
    /** Multiply each component by a finite scalar. */
    operator fun times(scale: Double): NpcVector3 = NpcVector3(x * scale, y * scale, z * scale)

    companion object {
        @JvmField val ZERO = NpcVector3(0.0, 0.0, 0.0)
        @JvmField val ONE = NpcVector3(1.0, 1.0, 1.0)
    }
}


private fun canonicalComponent(value: Double): Double {
    require(value.isFinite()) { "Non-finite vector" }
    return if (value == 0.0) 0.0 else value
}
