package gg.grounds.npc.core

import gg.grounds.npc.core.animation.NpcInterpolation
import gg.grounds.npc.core.io.NpcDecodeLimits
import gg.grounds.npc.core.io.NpcDecodeResult
import gg.grounds.npc.core.io.NpcModelJson

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcModelJsonDiagnosticsTest {
    @Test
    fun `invalid interpolation reports the exact authored field`() {
        val json = NpcCoreTest.fixture().replace("interpolation\":\"linear", "interpolation\":\"unsupported")
        val result = NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Failure
        assertEquals("$.animations.wave.tracks.body.position[0].interpolation", result.problems.single().path)
    }

    @Test
    fun `decode limits cannot promise more than model hard limits`() {
        assertThrows(IllegalArgumentException::class.java) { NpcDecodeLimits(maxBones = 257) }
        assertThrows(IllegalArgumentException::class.java) { NpcDecodeLimits(maxParts = 1025) }
        assertThrows(IllegalArgumentException::class.java) { NpcDecodeLimits(maxClips = 129) }
        assertThrows(IllegalArgumentException::class.java) { NpcDecodeLimits(maxKeysPerTrack = 4097) }
        assertThrows(IllegalArgumentException::class.java) { NpcDecodeLimits(maxClipSeconds = 301.0) }
    }
}
