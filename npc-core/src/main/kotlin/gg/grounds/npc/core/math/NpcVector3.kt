package gg.grounds.npc.core.math

/**
 * Finite immutable XYZ components. Units and coordinate space are defined by the owning API;
 * negative zero is canonicalized. Nonfinite inputs throw IllegalArgumentException.
 *
 * @property x Finite X component.
 * @property y Finite Y component.
 * @property z Finite Z component.
 * @property ZERO Vector with all three components zero.
 * @property ONE Vector with all three components one, the identity component scale.
 */
@ConsistentCopyVisibility
public data class NpcVector3
private constructor(
    /** Finite X component. */
    val x: Double,
    /** Finite Y component. */
    val y: Double,
    /** Finite Z component. */
    val z: Double,
    @Suppress("unused") private val canonical: Unit,
) {
    /** Construct finite XYZ components; nonfinite arguments throw IllegalArgumentException. */
    public constructor(
        x: Double,
        y: Double,
        z: Double,
    ) : this(canonicalComponent(x), canonicalComponent(y), canonicalComponent(z), Unit)

    /** Add model-local components without modifying either vector. */
    public operator fun plus(other: NpcVector3): NpcVector3 =
        NpcVector3(x + other.x, y + other.y, z + other.z)

    /** Subtract model-local components without modifying either vector. */
    public operator fun minus(other: NpcVector3): NpcVector3 =
        NpcVector3(x - other.x, y - other.y, z - other.z)

    /** Multiply each component by a finite scalar. */
    public operator fun times(scale: Double): NpcVector3 =
        NpcVector3(x * scale, y * scale, z * scale)

    /**
     * Shared factories and constants; Java entrypoints are exposed with their documented static
     * annotations.
     */
    public companion object {
        /** Vector with all three components zero. */
        @JvmField public val ZERO: NpcVector3 = NpcVector3(0.0, 0.0, 0.0)
        /** Vector with all three components one, the identity component scale. */
        @JvmField public val ONE: NpcVector3 = NpcVector3(1.0, 1.0, 1.0)
    }
}

private fun canonicalComponent(value: Double): Double {
    require(value.isFinite()) { "Non-finite vector" }
    return if (value == 0.0) 0.0 else value
}
