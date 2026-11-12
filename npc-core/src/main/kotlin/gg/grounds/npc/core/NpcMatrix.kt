// Extracted from Grounds resourcepacks v1.3.0, commit c92fcaffe95e02bf4a6badba700a4c40b5724016.
// See LICENSE and NOTICE for source provenance.
package gg.grounds.npc.core

import kotlin.math.cos
import kotlin.math.sin

class NpcMatrix(values: DoubleArray) {
    private val matrix = values.copyOf().also { require(it.size == 16 && it.all(Double::isFinite)) }
    val values: DoubleArray
        get() = matrix.copyOf()

    /** Read-only element access and comparison without allocating defensive array snapshots. */
    operator fun get(index: Int): Double = matrix[index]

    fun contentEquals(other: NpcMatrix): Boolean = matrix.contentEquals(other.matrix)

    operator fun times(other: NpcMatrix) =
        NpcMatrix(
            DoubleArray(16) { index ->
                (0..3).sumOf { matrix[(index / 4) * 4 + it] * other.matrix[it * 4 + index % 4] }
            }
        )

    fun point(p: NpcVector3) =
        NpcVector3(
            matrix[0] * p.x + matrix[1] * p.y + matrix[2] * p.z + matrix[3],
            matrix[4] * p.x + matrix[5] * p.y + matrix[6] * p.z + matrix[7],
            matrix[8] * p.x + matrix[9] * p.y + matrix[10] * p.z + matrix[11],
        )

    companion object {
        val IDENTITY =
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

        fun translation(p: NpcVector3) =
            NpcMatrix(
                IDENTITY.matrix.copyOf().also {
                    it[3] = p.x
                    it[7] = p.y
                    it[11] = p.z
                }
            )

        fun scale(p: NpcVector3) =
            NpcMatrix(
                IDENTITY.matrix.copyOf().also {
                    it[0] = p.x
                    it[5] = p.y
                    it[10] = p.z
                }
            )

        fun rotation(degrees: NpcVector3): NpcMatrix {
            val x = Math.toRadians(degrees.x)
            val y = Math.toRadians(degrees.y)
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
