package gg.grounds.npc.core.math

import kotlin.math.cos
import kotlin.math.sin

/**
 * Immutable row-major 4×4 affine matrix. Exactly 16 finite elements are required; input and
 * returned arrays are defensive copies. Numeric methods do not access platform state.
 *
 * @property values Defensive row-major 16-element matrix snapshot; changing it never changes this
 *   matrix.
 * @property IDENTITY Identity 4×4 matrix; use read-only access or defensive snapshots.
 */
public class NpcMatrix(values: DoubleArray) {
    private val matrix = values.copyOf().also { require(it.size == 16 && it.all(Double::isFinite)) }
    /** Defensive row-major 16-element matrix snapshot; changing it never changes this matrix. */
    public val values: DoubleArray
        get() = matrix.copyOf()

    /** Read-only element access and comparison without allocating defensive array snapshots. */
    public operator fun get(index: Int): Double = matrix[index]

    /** Compare all sixteen numeric elements without creating array snapshots. */
    public fun contentEquals(other: NpcMatrix): Boolean = matrix.contentEquals(other.matrix)

    /** Multiply this row-major matrix by the right operand; neither input is modified. */
    public operator fun times(other: NpcMatrix): NpcMatrix =
        NpcMatrix(
            DoubleArray(16) { index ->
                (0..3).sumOf { matrix[(index / 4) * 4 + it] * other.matrix[it * 4 + index % 4] }
            }
        )

    /** Transform an XYZ point with affine translation; returns a new finite vector. */
    public fun point(p: NpcVector3): NpcVector3 =
        NpcVector3(
            matrix[0] * p.x + matrix[1] * p.y + matrix[2] * p.z + matrix[3],
            matrix[4] * p.x + matrix[5] * p.y + matrix[6] * p.z + matrix[7],
            matrix[8] * p.x + matrix[9] * p.y + matrix[10] * p.z + matrix[11],
        )

    /**
     * Shared factories and constants; Java entrypoints are exposed with their documented static
     * annotations.
     */
    public companion object {
        /** Identity 4×4 matrix; use read-only access or defensive snapshots. */
        public val IDENTITY: NpcMatrix =
            NpcMatrix(
                doubleArrayOf(
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                )
            )

        /** Create an affine translation matrix from the supplied XYZ components. */
        public fun translation(p: NpcVector3): NpcMatrix =
            NpcMatrix(
                IDENTITY.matrix.copyOf().also {
                    it[3] = p.x
                    it[7] = p.y
                    it[11] = p.z
                }
            )

        /** Create an affine component-scale matrix; negative values represent reflection. */
        public fun scale(p: NpcVector3): NpcMatrix =
            NpcMatrix(
                IDENTITY.matrix.copyOf().also {
                    it[0] = p.x
                    it[5] = p.y
                    it[10] = p.z
                }
            )

        /**
         * Create an XYZ Euler rotation matrix from finite degrees, preserving the authored rotation
         * order.
         */
        public fun rotation(degrees: NpcVector3): NpcMatrix {
            /** Finite X component. */
            val x = Math.toRadians(degrees.x)
            /** Finite Y component. */
            val y = Math.toRadians(degrees.y)
            /** Finite Z component. */
            val z = Math.toRadians(degrees.z)
            val rx =
                NpcMatrix(
                    doubleArrayOf(
                        1.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        cos(x),
                        -sin(x),
                        0.0,
                        0.0,
                        sin(x),
                        cos(x),
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                    )
                )
            val ry =
                NpcMatrix(
                    doubleArrayOf(
                        cos(y),
                        0.0,
                        sin(y),
                        0.0,
                        0.0,
                        1.0,
                        0.0,
                        0.0,
                        -sin(y),
                        0.0,
                        cos(y),
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                    )
                )
            val rz =
                NpcMatrix(
                    doubleArrayOf(
                        cos(z),
                        -sin(z),
                        0.0,
                        0.0,
                        sin(z),
                        cos(z),
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                    )
                )
            return rz * ry * rx
        }
    }
}
