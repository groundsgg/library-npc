package gg.grounds.npc.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcCoreTest {
    private fun decoded(json: String = fixture()): NpcModel =
        (NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Success).model

    @Test
    fun `static numeric model can be decoded without an animation clip`() {
        val json =
            """{"schemaVersion":1,"asset":"example:static","bounds":{"center":[0,0,0],"size":[1,1,1]},"bones":[],"parts":[{"bone":null,"model":"example:cube","center":[0,0,0],"pivot":[0,0,0],"rotation":[0,0,0]}],"animations":{},"sounds":{}}"""
        val model = (NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Success).model
        assertTrue(model.animations.isEmpty())
        assertEquals(1, NpcPoseSampler.sample(model, null, 0.0).parts.size)
    }

    @Test
    fun `manually built definitions reject an invalid hierarchy`() {
        val valid = decoded()
        assertThrows(IllegalArgumentException::class.java) {
            NpcModel(
                valid.id,
                valid.bounds,
                listOf(NpcBone("head", "absent", NpcVector3.ZERO, NpcVector3.ZERO)),
                valid.parts,
                valid.animations,
                valid.sounds,
            )
        }
    }

    @Test
    fun `independent namespaces and linear tracks produce the authored pose`() {
        val model = decoded()
        assertEquals("demo:figure", model.id)
        val pose = NpcPoseSampler.sample(model, "wave", 0.5)
        assertEquals(1.0, pose.parts.first().point(NpcVector3.ZERO).x, 1e-9)
        assertEquals(1.0, pose.parts.first().point(NpcVector3.ZERO).y, 1e-9)
    }

    @Test
    fun `head look changes only its subtree`() {
        val model = decoded()
        val before = NpcPoseSampler.sample(model, "wave", 0.0)
        val after = NpcPoseSampler.sample(model, "wave", 0.0, NpcVector3(0.0, 35.0, 0.0))
        assertArrayEquals(before.parts[0].values, after.parts[0].values, 1e-9)
        assertFalse(before.parts[1].values.contentEquals(after.parts[1].values))
        assertEquals("head", model.headBone?.name)
    }

    @Test
    fun `pose snapshots cannot corrupt cached bind matrices`() {
        val model = decoded()
        val before = NpcPoseSampler.sample(model, "wave", 0.5)
        before.parts[0].values.fill(999.0)
        val after = NpcPoseSampler.sample(model, "wave", 0.5)
        assertEquals(1.0, after.parts[0].point(NpcVector3.ZERO).x, 1e-9)
        assertThrows(UnsupportedOperationException::class.java) {
            (model.bones as MutableList).clear()
        }
    }

    @Test
    fun `invalid inputs return structured failures without partially usable models`() {
        for (json in
            listOf(
                "{}",
                fixture().replace("\"parent\":null", "\"parent\":\"missing\""),
                fixture().replace("[0,1,0]", "[1e999,1,0]"),
                fixture().replace("\"linear\"", "\"expression\""),
            )) {
            val result = NpcModelJson.decode(json.toByteArray())
            assertInstanceOf(NpcDecodeResult.Failure::class.java, result)
            assertTrue((result as NpcDecodeResult.Failure).problems.isNotEmpty())
            assertTrue(result.problems.all { it.code.isNotBlank() && it.path.isNotBlank() })
        }
        val oversized = NpcModelJson.decode(fixture().toByteArray(), NpcDecodeLimits(maxBytes = 16))
        assertEquals(
            "NPC_MODEL_TOO_LARGE",
            (oversized as NpcDecodeResult.Failure).problems.single().code,
        )
    }

    @Test
    fun `negative scale and missing clip are handled explicitly`() {
        val model =
            decoded(
                fixture().replace("\"center\":[0,1,0]", "\"center\":[0,1,0],\"scale\":[-1,1,1]")
            )
        assertEquals(
            -1.0,
            NpcPoseSampler.sample(model, null, 0.0).parts[0].point(NpcVector3(1.0, 0.0, 0.0)).x,
            1e-9,
        )
        assertThrows(IllegalArgumentException::class.java) {
            NpcPoseSampler.sample(model, "absent", 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcPoseSampler.sample(model, "wave", Double.NaN)
        }
    }

    companion object {
        fun fixture() =
            """{
          "schemaVersion":1,"asset":"demo:figure","bounds":{"center":[0,1,0],"size":[1,2,1]},
          "bones":[{"id":"body","name":"body","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},
          {"id":"head","name":"head","parent":"body","origin":[0,1.5,0],"rotation":[0,0,0]}],
          "parts":[{"bone":"body","model":"demo:body","center":[0,1,0],"pivot":[0,0,0],"rotation":[0,0,0]},
          {"bone":"head","model":"demo:head","center":[0,1.75,-0.2],"pivot":[0,1.5,0],"rotation":[0,0,0]}],
          "animations":{"wave":{"loop":"loop","length":2,"tracks":{"body":{"position":[
          {"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":2,"value":[4,0,0],"interpolation":"linear"}]}}}},"sounds":{}
        }"""
    }
}
