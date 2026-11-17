package gg.grounds.npc.core

import gg.grounds.npc.core.animation.*
import gg.grounds.npc.core.animation.NpcInterpolation
import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.animation.NpcPoseSampler
import gg.grounds.npc.core.io.NpcDecodeResult
import gg.grounds.npc.core.io.NpcModelJson
import gg.grounds.npc.core.math.NpcVector3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcTypedAnimationTest {
    @Test
    fun `authored loop and interpolation become typed channels without changing numeric poses`() {
        val model =
            (NpcModelJson.decode(NpcCoreTest.fixture().toByteArray()) as NpcDecodeResult.Success)
                .model
        val clip = model.animations.getValue("wave")
        assertEquals(NpcLoopMode.LOOP, clip.loop)
        assertEquals(
            NpcInterpolation.LINEAR,
            clip.tracks.getValue("body").position.first().interpolation,
        )
        assertEquals(
            1.0,
            NpcPoseSampler.sample(model, "wave", 0.5).parts.first().point(NpcVector3.ZERO).x,
            1e-9,
        )
    }
}
