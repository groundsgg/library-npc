package gg.grounds.npc.importer

import gg.grounds.npc.core.animation.NpcPoseSampler
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BlockbenchAnimationTest {
    @TempDir lateinit var temp: Path

    @Test
    fun rejectsNonNeutralBlendWeightsWithTheirAuthoredField() {
        for (weight in listOf("0", "0.0", "0.5", "2")) {
            val json = BlockbenchFixture.model()
            val animation = BlockbenchFixture.animation("rotation", listOf(0.0, 45.0, 0.0))
            animation.addProperty("blend_weight", weight)
            json.getAsJsonArray("animations").add(animation)
            val error =
                assertThrows(NpcImportException::class.java) {
                    NpcBlockbenchImporter.importModel(
                        BlockbenchFixture.write(temp.resolve("weight.bbmodel"), json),
                        "demo:npc/cube",
                    )
                }
            assertTrue(error.problems.first().path.endsWith("blend_weight"))
        }
        val json = BlockbenchFixture.model()
        val animation = BlockbenchFixture.animation("rotation", listOf(0.0, 45.0, 0.0))
        animation.addProperty("blend_weight", "1.0")
        json.getAsJsonArray("animations").add(animation)
        assertTrue(
            NpcBlockbenchImporter.importModel(
                    BlockbenchFixture.write(temp.resolve("neutral.bbmodel"), json),
                    "demo:npc/cube",
                )
                .model
                .animations
                .containsKey("move")
        )
    }

    @Test
    fun rejectsExplicitAnimationClocksRatherThanSilentlyReplacingTheirTiming() {
        for (clock in listOf("0", "1", "query.anim_time + query.delta_time")) {
            val json = BlockbenchFixture.model()
            val animation = BlockbenchFixture.animation("position", listOf(16.0, 0.0, 0.0))
            animation.addProperty("anim_time_update", clock)
            json.getAsJsonArray("animations").add(animation)
            val error =
                assertThrows(NpcImportException::class.java) {
                    NpcBlockbenchImporter.importModel(
                        BlockbenchFixture.write(temp.resolve("clock.bbmodel"), json),
                        "demo:npc/cube",
                    )
                }
            assertTrue(error.problems.first().path.endsWith("anim_time_update"))
        }
    }

    @Test
    fun samplesAuthoredPositionInSecondsAndBlocks() {
        val json = BlockbenchFixture.model()
        json
            .getAsJsonArray("animations")
            .add(BlockbenchFixture.animation("position", listOf(16.0, 0.0, 0.0)))
        val model =
            NpcBlockbenchImporter.importModel(
                    BlockbenchFixture.write(temp.resolve("animated.bbmodel"), json),
                    "demo:npc/move",
                )
                .model
        val center = NpcPoseSampler.sample(model, "move", 0.5).parts.single().point(NpcVector3.ZERO)
        assertEquals(1.0, center.x, 1e-12)
        assertEquals(0.5, center.y, 1e-12)
    }

    @Test
    fun normalizesLegacyPositionAndRotationAxesToCurrentAuthoredPose() {
        for (channel in listOf("position", "rotation")) {
            val modern = BlockbenchFixture.model()
            modern
                .getAsJsonArray("animations")
                .add(BlockbenchFixture.animation(channel, listOf(16.0, 8.0, 4.0)))
            val legacy = modern.deepCopy()
            legacy.getAsJsonObject("meta").addProperty("format_version", "4.10")
            val group = legacy.getAsJsonArray("groups")[0].asJsonObject.deepCopy()
            group.add("children", legacy.getAsJsonArray("outliner")[0].asJsonObject["children"])
            legacy.getAsJsonArray("outliner").set(0, group)
            legacy.remove("groups")
            val point =
                legacy
                    .getAsJsonArray("animations")[0]
                    .asJsonObject
                    .getAsJsonObject("animators")
                    .getAsJsonObject("bone")
                    .getAsJsonArray("keyframes")[1]
                    .asJsonObject
                    .getAsJsonArray("data_points")[0]
                    .asJsonObject
            point.addProperty("x", -16.0)
            if (channel == "rotation") point.addProperty("y", -8.0)
            val a =
                NpcBlockbenchImporter.importModel(
                        BlockbenchFixture.write(temp.resolve("modern-$channel.bbmodel"), modern),
                        "demo:npc/move",
                    )
                    .model
            val b =
                NpcBlockbenchImporter.importModel(
                        BlockbenchFixture.write(temp.resolve("legacy-$channel.bbmodel"), legacy),
                        "demo:npc/move",
                    )
                    .model
            val expected = NpcPoseSampler.sample(a, "move", 0.5).parts.single().values
            val actual = NpcPoseSampler.sample(b, "move", 0.5).parts.single().values
            assertArrayEquals(expected, actual, 1e-12)
        }
    }

    @Test
    fun rejectsExpressionsAndUnsupportedInterpolationWithAuthoredPath() {
        for (interpolation in listOf("bezier", "linear")) {
            val json = BlockbenchFixture.model()
            json
                .getAsJsonArray("animations")
                .add(BlockbenchFixture.animation("position", listOf(1.0, 0.0, 0.0), interpolation))
            if (interpolation == "linear")
                json
                    .getAsJsonArray("animations")[0]
                    .asJsonObject
                    .getAsJsonObject("animators")
                    .getAsJsonObject("bone")
                    .getAsJsonArray("keyframes")[0]
                    .asJsonObject
                    .getAsJsonArray("data_points")[0]
                    .asJsonObject
                    .addProperty("x", "query.time")
            val error =
                assertThrows(NpcImportException::class.java) {
                    NpcBlockbenchImporter.importModel(
                        BlockbenchFixture.write(temp.resolve("bad.bbmodel"), json),
                        "demo:npc/bad",
                    )
                }
            assertTrue(error.problems.first().path.contains("animations"))
        }
    }
}
