package gg.grounds.npc.importer

import gg.grounds.npc.core.io.content.NpcContentLoader
import gg.grounds.npc.importer.config.*
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcSoundImportTest {
    @TempDir lateinit var temp: Path

    private fun input(): Path {
        val m = BlockbenchFixture.model()
        m.getAsJsonArray("animations")
            .add(BlockbenchFixture.animation("position", listOf(16.0, 0.0, 0.0)))
        return BlockbenchFixture.write(temp.resolve("cube.bbmodel"), m)
    }

    @Test
    fun mergesDisjointSoundDefinitionsAndBindsTheContributedSubset() {
        val input = input()
        Files.write(temp.resolve("wave.ogg"), "OggS".toByteArray() + ByteArray(30))
        val config =
            Files.writeString(
                temp.resolve("sounds.json"),
                """{"schemaVersion":1,"sounds":[{"model":"demo:npc/cube","clip":"move","key":"demo:npc/wave","file":"wave.ogg","volume":0.8,"pitch":1.0,"radius":12.0}]}""",
            )
        val base =
            PackFixture.zip(
                temp.resolve("base.zip"),
                mapOf(
                    "pack.mcmeta" to PackFixture.metadata(),
                    "assets/demo/sounds.json" to
                        """{"foreign":{"sounds":["demo:foreign"]}}""".toByteArray(),
                ),
            )
        val output =
            NpcModelImporter.compile(
                NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3)
                    .config(config)
                    .basePack(base)
                    .build(),
                temp.resolve("output"),
            )
        val content = NpcContentLoader.load(output.serverContent)
        content.verifyResourcePack(output.resourcePack)
        assertEquals("demo:npc/wave", content.models.values.single().sounds.getValue("move").key)
        val files = PackFixture.read(output.resourcePack)
        assertTrue(String(files.getValue("assets/demo/sounds.json")).contains("foreign"))
        assertTrue("assets/demo/sounds/npc/wave.ogg" in files)
        val changed =
            PackFixture.zip(
                temp.resolve("changed.zip"),
                files +
                    ("assets/demo/sounds.json" to
                        """{"npc/wave":{"sounds":["demo:other"]}}""".toByteArray()),
            )
        assertThrows(Exception::class.java) { content.verifyResourcePack(changed) }
    }

    @Test
    fun acceptsExplicitExternalSoundAndRejectsUnboundOrUnknownClip() {
        val input = input()
        val config =
            Files.writeString(
                temp.resolve("import.json"),
                """{"schemaVersion":1,"sounds":[{"model":"demo:npc/cube","clip":"move","key":"minecraft:entity.villager.yes","external":true}]}""",
            )
        val options =
            NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3)
                .config(config)
                .build()
        val result = NpcModelImporter.compile(options, temp.resolve("external"))
        NpcContentLoader.load(result.serverContent).verifyResourcePack(result.resourcePack)
        Files.writeString(
            config,
            """{"schemaVersion":1,"sounds":[{"model":"demo:npc/cube","clip":"unknown","key":"demo:missing"}]}""",
        )
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options, temp.resolve("invalid"))
        }
    }
}
