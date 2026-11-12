// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta

/** Converts scene's ordered affine transform into Minecraft display TRSR metadata. */
internal class DisplayTransform
private constructor(
    private val translation: NpcVector3,
    private val left: DoubleArray,
    private val scale: DoubleArray,
    private val right: DoubleArray,
) {
    /**
     * Returns whether serialized pose fields changed, so callers can restart interpolation once.
     */
    fun apply(meta: AbstractDisplayMeta, anchor: NpcVector3 = NpcVector3(0.0, 0.0, 0.0)): Boolean {
        val nextTranslation =
            Vec(
                (translation.x - anchor.x).toFloat().toDouble(),
                (translation.y - anchor.y).toFloat().toDouble(),
                (translation.z - anchor.z).toFloat().toDouble(),
            )
        val nextLeft = quaternion(left)
        // VECTOR3 metadata is serialized as floats, despite Vec storing doubles.
        val nextScale =
            Vec(
                scale[0].toFloat().toDouble(),
                scale[1].toFloat().toDouble(),
                scale[2].toFloat().toDouble(),
            )
        val nextRight = quaternion(right)
        val translationChanged = meta.translation != nextTranslation
        val leftChanged = !meta.leftRotation.contentEquals(nextLeft)
        val scaleChanged = meta.scale != nextScale
        val rightChanged = !meta.rightRotation.contentEquals(nextRight)
        if (translationChanged) meta.setTranslation(nextTranslation)
        if (leftChanged) meta.setLeftRotation(nextLeft)
        if (scaleChanged) meta.setScale(nextScale)
        if (rightChanged) meta.setRightRotation(nextRight)
        return translationChanged || leftChanged || scaleChanged || rightChanged
    }

    private fun quaternion(rotation: DoubleArray): FloatArray {
        val trace = rotation[0] + rotation[4] + rotation[8]
        return if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0
            floatArrayOf(
                ((rotation[7] - rotation[5]) / s).toFloat(),
                ((rotation[2] - rotation[6]) / s).toFloat(),
                ((rotation[3] - rotation[1]) / s).toFloat(),
                (s / 4.0).toFloat(),
            )
        } else if (rotation[0] > rotation[4] && rotation[0] > rotation[8]) {
            val s = sqrt(1.0 + rotation[0] - rotation[4] - rotation[8]) * 2.0
            floatArrayOf(
                (s / 4.0).toFloat(),
                ((rotation[1] + rotation[3]) / s).toFloat(),
                ((rotation[2] + rotation[6]) / s).toFloat(),
                ((rotation[7] - rotation[5]) / s).toFloat(),
            )
        } else if (rotation[4] > rotation[8]) {
            val s = sqrt(1.0 + rotation[4] - rotation[0] - rotation[8]) * 2.0
            floatArrayOf(
                ((rotation[1] + rotation[3]) / s).toFloat(),
                (s / 4.0).toFloat(),
                ((rotation[5] + rotation[7]) / s).toFloat(),
                ((rotation[2] - rotation[6]) / s).toFloat(),
            )
        } else {
            val s = sqrt(1.0 + rotation[8] - rotation[0] - rotation[4]) * 2.0
            floatArrayOf(
                ((rotation[2] + rotation[6]) / s).toFloat(),
                ((rotation[5] + rotation[7]) / s).toFloat(),
                (s / 4.0).toFloat(),
                ((rotation[3] - rotation[1]) / s).toFloat(),
            )
        }
    }

    companion object {
        fun from(transform: NpcRenderTransform, bounds: NpcBounds): DisplayTransform {
            val root = affine(transform.root)
            val local = transform.local?.let(::affine) ?: Affine.IDENTITY
            val boundsAffine =
                Affine(
                    NpcVector3(
                        bounds.center.x - bounds.size.x / 2,
                        bounds.center.y - bounds.size.y / 2,
                        bounds.center.z - bounds.size.z / 2,
                    ),
                    Matrix3.scale(bounds.size),
                )
            val affine = root * local * boundsAffine
            val decomposition = decompose(affine.matrix)
            return DisplayTransform(
                affine.translation,
                decomposition.left,
                decomposition.scale,
                decomposition.right,
            )
        }

        fun fromModel(transform: NpcRenderTransform, model: NpcMatrix): DisplayTransform {
            val m = model
            val modelAffine =
                Affine(
                    NpcVector3(m[3], m[7], m[11]),
                    Matrix3(doubleArrayOf(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10])),
                )
            val composed =
                affine(transform.root) *
                    (transform.local?.let(::affine) ?: Affine.IDENTITY) *
                    modelAffine
            val decomposed = decompose(composed.matrix)
            return DisplayTransform(
                composed.translation,
                decomposed.left,
                decomposed.scale,
                decomposed.right,
            )
        }

        private fun affine(transform: NpcTransform): Affine =
            Affine(transform.position, Matrix3.rotation(transform) * Matrix3.scale(transform.scale))

        private fun decompose(matrix: Matrix3): Decomposition {
            val symmetric = matrix.transpose() * matrix
            val (eigenvectors, eigenvalues) = symmetric.eigen()
            val scale = DoubleArray(3) { sqrt(max(0.0, eigenvalues[it])) }
            val right = eigenvectors.transpose()
            val left =
                matrix *
                    eigenvectors *
                    Matrix3.diagonal(
                        DoubleArray(3) { if (scale[it] == 0.0) 0.0 else 1.0 / scale[it] }
                    )
            if (matrix.determinant() < 0.0) scale[2] = -scale[2]
            return Decomposition(left.orthonormalized(), scale, right.orthonormalized())
        }
    }
}

private data class Decomposition(
    val left: DoubleArray,
    val scale: DoubleArray,
    val right: DoubleArray,
)

private data class Affine(val translation: NpcVector3, val matrix: Matrix3) {
    operator fun times(other: Affine) =
        Affine(
            translation +
                matrix *
                    doubleArrayOf(other.translation.x, other.translation.y, other.translation.z),
            matrix * other.matrix,
        )

    companion object {
        val IDENTITY = Affine(NpcVector3(0.0, 0.0, 0.0), Matrix3.IDENTITY)
    }
}

private operator fun NpcVector3.plus(other: DoubleArray) =
    NpcVector3(x + other[0], y + other[1], z + other[2])

private class Matrix3(private val values: DoubleArray) {
    operator fun times(other: Matrix3) =
        Matrix3(
            DoubleArray(9) { i ->
                val row = i / 3
                val col = i % 3
                (0..2).sumOf { values[row * 3 + it] * other.values[it * 3 + col] }
            }
        )

    operator fun times(vector: DoubleArray) =
        DoubleArray(3) { row -> (0..2).sumOf { values[row * 3 + it] * vector[it] } }

    fun transpose() =
        Matrix3(
            doubleArrayOf(
                values[0],
                values[3],
                values[6],
                values[1],
                values[4],
                values[7],
                values[2],
                values[5],
                values[8],
            )
        )

    fun determinant(): Double =
        values[0] * (values[4] * values[8] - values[5] * values[7]) -
            values[1] * (values[3] * values[8] - values[5] * values[6]) +
            values[2] * (values[3] * values[7] - values[4] * values[6])

    fun orthonormalized(): DoubleArray {
        val columns = Array(3) { c -> DoubleArray(3) { r -> values[r * 3 + c] } }
        val present = columns.map { dot(it, it) > 1e-20 }
        fun perpendicular(axis: DoubleArray): DoubleArray {
            val hint =
                if (abs(axis[0]) < .8) doubleArrayOf(1.0, 0.0, 0.0)
                else doubleArrayOf(0.0, 1.0, 0.0)
            return normalize(subtract(hint, scale(axis, dot(axis, hint))))
        }
        // Preserve every surviving axis when authored animation collapses one or two axes.
        val (x, y, z) =
            when {
                present[0] && present[1] -> {
                    val x = normalize(columns[0])
                    val y = normalize(subtract(columns[1], scale(x, dot(x, columns[1]))))
                    Triple(x, y, cross(x, y))
                }
                present[0] && present[2] -> {
                    val x = normalize(columns[0])
                    val z = normalize(subtract(columns[2], scale(x, dot(x, columns[2]))))
                    Triple(x, cross(z, x), z)
                }
                present[1] && present[2] -> {
                    val y = normalize(columns[1])
                    val z = normalize(subtract(columns[2], scale(y, dot(y, columns[2]))))
                    Triple(cross(y, z), y, z)
                }
                present[0] -> {
                    val x = normalize(columns[0])
                    val y = perpendicular(x)
                    Triple(x, y, cross(x, y))
                }
                present[1] -> {
                    val y = normalize(columns[1])
                    val z = perpendicular(y)
                    Triple(cross(y, z), y, z)
                }
                present[2] -> {
                    val z = normalize(columns[2])
                    val x = perpendicular(z)
                    Triple(x, cross(z, x), z)
                }
                else ->
                    Triple(
                        doubleArrayOf(1.0, 0.0, 0.0),
                        doubleArrayOf(0.0, 1.0, 0.0),
                        doubleArrayOf(0.0, 0.0, 1.0),
                    )
            }
        return doubleArrayOf(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])
    }

    fun eigen(): Pair<Matrix3, DoubleArray> {
        val a = values.copyOf()
        val v = IDENTITY.values.copyOf()
        for (iteration in 0 until 24) {
            var p = 0
            var q = 1
            if (abs(a[2]) > abs(a[p * 3 + q])) q = 2
            if (abs(a[5]) > abs(a[p * 3 + q])) {
                p = 1
                q = 2
            }
            if (abs(a[p * 3 + q]) < 1e-12) break
            val angle = 0.5 * atan2(2 * a[p * 3 + q], a[q * 3 + q] - a[p * 3 + p])
            val c = cos(angle)
            val s = sin(angle)
            for (i in 0..2) {
                val ip = a[i * 3 + p]
                val iq = a[i * 3 + q]
                a[i * 3 + p] = c * ip - s * iq
                a[i * 3 + q] = s * ip + c * iq
            }
            for (i in 0..2) {
                val pi = a[p * 3 + i]
                val qi = a[q * 3 + i]
                a[p * 3 + i] = c * pi - s * qi
                a[q * 3 + i] = s * pi + c * qi
            }
            for (i in 0..2) {
                val ip = v[i * 3 + p]
                val iq = v[i * 3 + q]
                v[i * 3 + p] = c * ip - s * iq
                v[i * 3 + q] = s * ip + c * iq
            }
        }
        return Matrix3(v) to doubleArrayOf(a[0], a[4], a[8])
    }

    companion object {
        val IDENTITY = Matrix3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        fun diagonal(v: DoubleArray) =
            Matrix3(doubleArrayOf(v[0], 0.0, 0.0, 0.0, v[1], 0.0, 0.0, 0.0, v[2]))

        fun scale(v: NpcVector3) = diagonal(doubleArrayOf(v.x, v.y, v.z))

        fun rotation(t: NpcTransform): Matrix3 {
            fun r(axis: Char, degrees: Double): Matrix3 {
                val c = cos(Math.toRadians(degrees))
                val s = sin(Math.toRadians(degrees))
                return when (axis) {
                    'x' -> Matrix3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c))
                    'y' -> Matrix3(doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c))
                    else -> Matrix3(doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0))
                }
            }
            return r('y', t.rotationDegrees.y) *
                r('x', t.rotationDegrees.x) *
                r('z', t.rotationDegrees.z)
        }

        fun fromQuaternion(q: FloatArray): Matrix3 {
            val x = q[0].toDouble()
            val y = q[1].toDouble()
            val z = q[2].toDouble()
            val w = q[3].toDouble()
            return Matrix3(
                doubleArrayOf(
                    1 - 2 * y * y - 2 * z * z,
                    2 * x * y - 2 * z * w,
                    2 * x * z + 2 * y * w,
                    2 * x * y + 2 * z * w,
                    1 - 2 * x * x - 2 * z * z,
                    2 * y * z - 2 * x * w,
                    2 * x * z - 2 * y * w,
                    2 * y * z + 2 * x * w,
                    1 - 2 * x * x - 2 * y * y,
                )
            )
        }
    }
}

private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

private fun scale(a: DoubleArray, s: Double) = DoubleArray(3) { a[it] * s }

private fun subtract(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] - b[it] }

private fun normalize(a: DoubleArray): DoubleArray {
    val n = sqrt(dot(a, a))
    return DoubleArray(3) { a[it] / n }
}

private fun cross(a: DoubleArray, b: DoubleArray) =
    doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
