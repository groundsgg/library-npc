package gg.grounds.npc.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcSamplingTest {
    private fun modelFrom(json: String) =
        (NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Success).model

    @Test
    fun `sampling a fifty part skeleton keeps temporary allocation bounded`() {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean()
        org.junit.jupiter.api.Assumptions.assumeTrue(bean is com.sun.management.ThreadMXBean)
        val allocation = bean as com.sun.management.ThreadMXBean
        val bones =
            (0 until 12).map {
                NpcBone("bone-$it", if (it == 0) null else "bone-${it - 1}", ZERO, ZERO)
            }
        val model =
            NpcModel(
                "test:allocation",
                NpcBounds(ZERO, ONE),
                bones,
                (0 until 50).map {
                    NpcPart(
                        "bone-${it % 12}",
                        "test:part-$it",
                        NpcVector3(0.0, it / 50.0, 0.0),
                        ZERO,
                        ZERO,
                    )
                },
                mapOf("idle" to NpcClip("loop", 2.0, emptyMap())),
                emptyMap(),
            )
        repeat(1000) { model.pose("idle", it / 1000.0, ZERO) }
        val id = Thread.currentThread().threadId()
        val before = allocation.getThreadAllocatedBytes(id)
        var sampled = 0.0
        repeat(100) { sampled += model.pose("idle", it / 1000.0, ZERO).last().values[7] }
        val perPose = (allocation.getThreadAllocatedBytes(id) - before) / 100
        assertEquals(98.0, sampled, 1e-8)
        assertTrue(perPose < 50_000, "Allocated $perPose bytes per fifty-part pose")
    }

    @Test
    fun `named head receives additive look while body stays still and head children follow`() {
        val model =
            modelFrom(
                """{
          "schemaVersion":1,"asset":"grounds:npc/test/head","bounds":{"center":[0,1,0],"size":[1,2,1]},
          "bones":[{"id":"body-id","name":"body","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},
          {"id":"head-id","name":"head","parent":"body-id","origin":[0,1.5,0],"rotation":[0,0,0]},
          {"id":"hat-id","name":"hat","parent":"head-id","origin":[0,2,0],"rotation":[0,0,0]}],
          "parts":[{"bone":"body-id","model":"grounds:npc_parts/test/body","center":[0,0.7,0],"pivot":[0,0.7,0],"rotation":[0,0,0]},
          {"bone":"head-id","model":"grounds:npc_parts/test/head","center":[0,1.75,0],"pivot":[0,1.75,0],"rotation":[0,0,0]},
          {"bone":"hat-id","model":"grounds:npc_parts/test/hat","center":[0,2.1,0],"pivot":[0,2.1,0],"rotation":[0,0,0]}],
          "animations":{"idle":{"loop":"loop","length":2,"tracks":{"body-id":{"position":[{"time":0,"value":[0.2,0,0],"interpolation":"linear"}]}}}},"sounds":{}
        }"""
            )
        assertEquals("head-id", model.headBone!!.id)
        val original = model.pose("idle", 0.5)
        val looked = model.pose("idle", 0.5, NpcVector3(30.0, 45.0, 0.0))
        assertArrayEquals(original[0].values, looked[0].values, 1e-12)
        assertNotEquals(original[1].point(ZERO), looked[1].point(ZERO))
        assertNotEquals(original[2].point(ZERO), looked[2].point(ZERO))
        val facing = NpcMatrix.rotation(NpcVector3(0.0, 180.0, 0.0))
        val world = facing * looked[1]
        val origin = world.point(ZERO)
        val forward = world.point(NpcVector3(0.0, 0.0, -1.0)) - origin
        assertTrue(forward.x > 0.0, "Head must turn toward +X")
        assertTrue(
            forward.y > 0.0,
            "Negative Scene pitch must look upward after model-facing correction",
        )
        assertArrayEquals(
            original[0].values,
            model.pose("idle", 0.5)[0].values,
            1e-12,
            "Look must not mutate original clips",
        )
    }

    @Test
    fun `looping Catmull Rom curves wrap their tangent keys at both ends`() {
        val keys =
            listOf(91.6409, 92.7091, 91.6409, 88.6579, 91.6409).mapIndexed { i, value ->
                NpcKeyframe(i.toDouble(), NpcVector3(value, 0.0, 0.0), "catmullrom")
            }
        assertEquals(92.4282, sampleNpcTrack(keys, 0.5, ZERO, true).x, 1e-7)
        assertEquals(89.8962, sampleNpcTrack(keys, 3.5, ZERO, true).x, 1e-7)
    }

    @Test
    fun `linear and Catmull Rom samples retain the source curve rather than snapping keys`() {
        val linear =
            listOf(
                NpcKeyframe(0.0, NpcVector3(0.0, 0.0, 0.0), "linear"),
                NpcKeyframe(2.0, NpcVector3(4.0, 0.0, 0.0), "linear"),
            )
        assertEquals(1.0, sampleNpcTrack(linear, 0.5, NpcVector3(9.0, 9.0, 9.0)).x, 1e-9)
        val curve =
            listOf(
                NpcKeyframe(0.0, NpcVector3(0.0, 0.0, 0.0), "catmullrom"),
                NpcKeyframe(1.0, NpcVector3(2.0, 0.0, 0.0), "catmullrom"),
                NpcKeyframe(2.0, NpcVector3(0.0, 0.0, 0.0), "catmullrom"),
            )
        assertEquals(1.125, sampleNpcTrack(curve, 0.5, NpcVector3(0.0, 0.0, 0.0)).x, 1e-9)
        assertEquals(2.0, sampleNpcTrack(curve, 1.0, NpcVector3(0.0, 0.0, 0.0)).x, 1e-9)
    }

    @Test
    fun `nested bones rotate cube centers around the source pivots`() {
        val model =
            modelFrom(
                """{
          "schemaVersion":1,"asset":"grounds:npc/test/worker","bounds":{"center":[0,1,0],"size":[1,2,1]},
          "bones":[{"id":"root","name":"root","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},
                   {"id":"arm","name":"arm","parent":"root","origin":[0,1,0],"rotation":[0,0,0]}],
          "parts":[{"bone":"arm","model":"grounds:npc_parts/test/worker/part_000","center":[1,1,0],"pivot":[1,1,0],"rotation":[0,0,0]}],
          "animations":{"idle":{"loop":"loop","length":2,"tracks":{"root":{"position":[{"time":0,"value":[2,0,0],"interpolation":"linear"}]},"arm":{"rotation":[{"time":0,"value":[0,0,90],"interpolation":"linear"}]}}}},"sounds":{}
        }"""
            )
        val pose = model.pose("idle", 0.0).single()
        val center = pose.point(NpcVector3(0.0, 0.0, 0.0))
        assertEquals(2.0, center.x, 1e-9)
        assertEquals(2.0, center.y, 1e-9)
        assertEquals(0.0, center.z, 1e-9)
        val inverted =
            NpcModel(
                model.id,
                model.bounds,
                model.bones,
                model.parts.map { it.copy(scale = NpcVector3(-1.0, -1.0, -1.0)) },
                model.animations,
                model.sounds,
            )
        val edge = inverted.pose("idle", 0.0).single().point(NpcVector3(1.0, 0.0, 0.0))
        assertEquals(2.0, edge.x, 1e-9)
        assertEquals(1.0, edge.y, 1e-9)
    }

    @Test
    fun `malformed models cannot introduce missing bones cyclic hierarchy or expressions`() {
        assertInstanceOf(
            NpcDecodeResult.Failure::class.java,
            NpcModelJson.decode("{}".toByteArray()),
        )
    }
}
