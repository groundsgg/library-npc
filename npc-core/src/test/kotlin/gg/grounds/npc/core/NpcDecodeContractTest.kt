package gg.grounds.npc.core

import com.google.gson.JsonParser
import gg.grounds.npc.core.io.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcDecodeContractTest {
    private fun problem(json: String, limits: NpcDecodeLimits = NpcDecodeLimits()) =
        (NpcModelJson.decode(json.toByteArray(), limits) as NpcDecodeResult.Failure)
            .problems
            .single()

    @Test
    fun `dynamic keys and component indices identify the actual failing field`() {
        val dotted = NpcCoreTest.fixture().replace("body", "left.arm").replace("wave", "wave.fast")
        val prefix = "$.animations[\"wave.fast\"].tracks[\"left.arm\"].position[0]"
        assertEquals("$prefix.interpolation", problem(dotted.replace("linear", "invalid")).path)
        assertEquals("$prefix.time", problem(dotted.replace("\"time\":0", "\"time\":-1")).path)
        assertEquals(
            "$prefix.value[1]",
            problem(dotted.replace("\"value\":[0,0,0]", "\"value\":[0,1e999,0]")).path,
        )
        assertEquals(
            "$.bounds.size",
            problem(dotted.replace("\"size\":[1,2,1]", "\"size\":[0,2,1]")).path,
        )
    }

    @Test
    fun `budgets reject oversized collections before decoding their contents`() {
        fun invalidItem(field: String): String {
            val json = JsonParser.parseString(NpcCoreTest.fixture()).asJsonObject
            json.getAsJsonArray(field).set(0, JsonParser.parseString("null"))
            return json.toString()
        }
        assertEquals("$.bones", problem(invalidItem("bones"), NpcDecodeLimits(maxBones = 1)).path)
        assertEquals("$.parts", problem(invalidItem("parts"), NpcDecodeLimits(maxParts = 1)).path)
        val json = JsonParser.parseString(NpcCoreTest.fixture()).asJsonObject
        val clips = json.getAsJsonObject("animations")
        clips.add("other", clips.get("wave").deepCopy())
        clips.add("wave", JsonParser.parseString("null"))
        assertEquals("$.animations", problem(json.toString(), NpcDecodeLimits(maxClips = 1)).path)
        val keys = NpcCoreTest.fixture().replace("\"time\":0", "\"time\":\"invalid\"")
        assertEquals(
            "$.animations.wave.tracks.body.position",
            problem(keys, NpcDecodeLimits(maxKeysPerTrack = 1)).path,
        )
        assertEquals(
            "$.animations.wave.length",
            problem(keys, NpcDecodeLimits(maxClipSeconds = 1.0)).path,
        )
    }

    @Test
    fun `budget boundaries duplicate bones sorted keys and malformed utf8 are explicit`() {
        assertInstanceOf(
            NpcDecodeResult.Success::class.java,
            NpcModelJson.decode(
                NpcCoreTest.fixture().toByteArray(),
                NpcDecodeLimits(
                    maxBones = 2,
                    maxParts = 2,
                    maxClips = 1,
                    maxKeysPerTrack = 2,
                    maxClipSeconds = 2.0,
                ),
            ),
        )
        assertEquals(
            "$.bones[1].id",
            problem(NpcCoreTest.fixture().replace("\"id\":\"head\"", "\"id\":\"body\"")).path,
        )
        assertEquals(
            "$.animations.wave.tracks.body.position[1].time",
            problem(
                    NpcCoreTest.fixture()
                        .replace("\"time\":0", "\"time\":2")
                        .replace("\"time\":2,\"value\":[4", "\"time\":1,\"value\":[4")
                )
                .path,
        )
        val failure =
            NpcModelJson.decode(byteArrayOf(0xc3.toByte(), 0x28)) as NpcDecodeResult.Failure
        assertEquals("NPC_MODEL_INVALID_UTF8", failure.problems.single().code)
    }
}
