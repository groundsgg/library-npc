package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.lang.management.ManagementFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class NpcDisplayPosesTest {
    @Test
    fun `cached corrected sampling retains original fifty part allocation budget including comparisons`() {
        val allocation = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        assumeTrue(allocation != null && allocation.isThreadAllocatedMemorySupported)
        allocation!!.isThreadAllocatedMemoryEnabled = true
        val bones =
            (0 until 12).map {
                NpcBone("bone-$it", if (it == 0) null else "bone-${it-1}", ZERO, ZERO)
            }
        val model =
            NpcModel(
                "test:allocation",
                NpcBounds(ZERO, ONE),
                bones,
                (0 until 50).map {
                    NpcPart(
                        "bone-${it%12}",
                        "test:part-$it",
                        NpcVector3(0.0, it / 50.0, 0.0),
                        ZERO,
                        ZERO,
                    )
                },
                mapOf("idle" to NpcClip("loop", 2.0, emptyMap())),
                emptyMap(),
            )
        val sampler = NpcDisplayPoses(model)
        var previous = sampler.sample("idle", 0.0, ZERO)
        repeat(1000) { previous = sampler.sample("idle", it / 1000.0, ZERO) }
        val id = Thread.currentThread().threadId()
        val before = allocation.getThreadAllocatedBytes(id)
        var sampled = 0.0
        repeat(100) {
            val pose = sampler.sample("idle", it / 1000.0, ZERO)
            check(pose.indices.all { index -> previous[index].contentEquals(pose[index]) })
            sampled += pose.last()[7]
            previous = pose
        }
        val perPose = (allocation.getThreadAllocatedBytes(id) - before) / 100
        assertEquals(98.0, sampled, 1e-8)
        assertTrue(
            perPose < 50_000,
            "Corrected renderer path allocated $perPose bytes per fifty-part pose",
        )
    }

    @Test
    fun `prepared binding preserves authored head look and right mesh correction order`() {
        val model = NpcLifecycleTest.model()
        val correction = NpcMatrix.rotation(NpcVector3(0.0, 180.0, 0.0))
        val head = NpcVector3(25.0, 35.0, 0.0)
        val expected = NpcPoseSampler.sample(model, null, 0.5, head).parts.map { it * correction }
        val actual = NpcDisplayPoses(model).sample(null, 0.5, head)
        expected.zip(actual).forEach { (want, got) ->
            assertArrayEquals(want.values, got.values, 1e-12)
        }
    }
}
