package gg.grounds.npc.importer

import com.google.gson.JsonParser
import gg.grounds.npc.core.io.content.NpcContentLoader
import gg.grounds.npc.importer.config.*
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcCompilationTest {
    @TempDir lateinit var temp: Path

    private fun options(input: Path) =
        NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3).build()

    @Test
    fun missingConfiguredPackPreservesTheIoFailure() {
        val input = BlockbenchFixture.write(temp.resolve("cube.bbmodel"))
        val options =
            NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3)
                .basePack(temp.resolve("missing.zip"))
                .build()
        assertThrows(java.io.IOException::class.java) {
            NpcModelImporter.compile(options, temp.resolve("output"))
        }
    }

    @Test
    fun malformedSourceJsonRemainsAnAuthoredDiagnostic() {
        val input = Files.writeString(temp.resolve("bad.bbmodel"), "{")
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(input), temp.resolve("output"))
        }
    }

    @Test
    fun compilesAndLoadsAnEntireIndependentContentSet() {
        val input = temp.resolve("models")
        BlockbenchFixture.write(input.resolve("town/merchant.bbmodel"))
        val output = NpcModelImporter.compile(options(input), temp.resolve("output"))
        val content = NpcContentLoader.load(output.serverContent)
        assertEquals(setOf("demo:npc/town/merchant"), content.models.keys)
        content.verifyResourcePack(output.resourcePack)
        assertTrue(Files.isDirectory(output.packFragment))
        val manifest = JsonParser.parseString(Files.readString(output.manifest)).asJsonObject
        assertEquals(content.contentId, manifest["contentId"].asString)
        assertFalse(Files.readString(output.manifest).contains(temp.toString()))
    }

    @Test
    fun validatesWithoutPublishingAndRejectsAnInvalidMemberAtomically() {
        val input = temp.resolve("models")
        BlockbenchFixture.write(input.resolve("good.bbmodel"))
        assertEquals(emptyList<Any>(), NpcModelImporter.validate(options(input)))
        assertEquals(
            setOf("models"),
            Files.list(temp).use { it.map { p -> p.fileName.toString() }.toList().toSet() },
        )
        Files.writeString(input.resolve("bad.bbmodel"), "{}")
        assertFalse(NpcModelImporter.validate(options(input)).isEmpty())
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(input), temp.resolve("failed"))
        }
        assertFalse(Files.exists(temp.resolve("failed")))
    }

    @Test
    fun preservesExistingOutputAndRejectsNormalizedModelIdCollisions() {
        val input = temp.resolve("models")
        BlockbenchFixture.write(input.resolve("Cube.bbmodel"))
        BlockbenchFixture.write(input.resolve("cube.bbmodel"))
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(input), temp.resolve("collision"))
        }
        Files.delete(input.resolve("Cube.bbmodel"))
        val output = temp.resolve("existing")
        Files.createDirectories(output)
        Files.writeString(output.resolve("keep"), "original")
        assertThrows(Exception::class.java) { NpcModelImporter.compile(options(input), output) }
        assertEquals("original", Files.readString(output.resolve("keep")))
        assertEquals(1L, Files.list(output).use { it.count() })
    }

    @Test
    fun supportsExplicitProductIdsWithoutProductKnowledgeInTheImporter() {
        val input = BlockbenchFixture.write(temp.resolve("cube.bbmodel"))
        val config =
            Files.writeString(
                temp.resolve("import.json"),
                """{"schemaVersion":1,"models":{"cube.bbmodel":"custom:npc/actor"}}""",
            )
        val output =
            NpcModelImporter.compile(
                NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_2)
                    .config(config)
                    .build(),
                temp.resolve("output"),
            )
        val content = NpcContentLoader.load(output.serverContent)
        assertEquals(setOf("custom:npc/actor"), content.models.keys)
        content.verifyResourcePack(output.resourcePack)
    }
}
