package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class DisplayQuaternionTest {
    private val target =
        DisplayTransform.fromModel(
            SceneRenderTransform(
                Transform(Vec3(0.0, 0.0, 0.0), EulerRotation(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 1.0)),
                null,
            ),
            NpcMatrix.scale(Vec3(1.0, 1.0, 1.0)),
        )
    private val conversion =
        DisplayTransform::class
            .java
            .getDeclaredMethod("quaternion", DoubleArray::class.java)
            .apply { isAccessible = true }

    private fun convert(matrix: DoubleArray) = conversion.invoke(target, matrix) as FloatArray

    @Test
    fun `identity and half turns cover all four conversion branches`() {
        val cases =
            listOf(
                doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0) to
                    floatArrayOf(0f, 0f, 0f, 1f),
                doubleArrayOf(1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, -1.0) to
                    floatArrayOf(1f, 0f, 0f, 0f),
                doubleArrayOf(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.0) to
                    floatArrayOf(0f, 1f, 0f, 0f),
                doubleArrayOf(-1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 1.0) to
                    floatArrayOf(0f, 0f, 1f, 0f),
            )
        for ((matrix, expected) in cases) {
            assertArrayEquals(
                expected.map(Float::toRawBits).toIntArray(),
                convert(matrix).map(Float::toRawBits).toIntArray(),
            )
        }
    }

    @Test
    fun `trace zero and equal diagonals preserve quaternion component ordering`() {
        // 120 degrees around (1,1,1): trace zero and all three diagonals equal.
        val cyclic = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertArrayEquals(floatArrayOf(.5f, .5f, .5f, .5f), convert(cyclic))
        // 180 degrees around (1,1,0): the first two diagonal entries tie.
        val xy = doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, -1.0)
        val halfRoot = (kotlin.math.sqrt(2.0) / 2.0).toFloat()
        assertArrayEquals(floatArrayOf(halfRoot, halfRoot, 0f, 0f), convert(xy))
    }

    @Test
    fun `each conversion owns its returned components`() {
        val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val first = convert(identity)
        val second = convert(identity)
        assertNotSame(first, second)
        first.fill(17f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), second)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), convert(identity))
    }
}
