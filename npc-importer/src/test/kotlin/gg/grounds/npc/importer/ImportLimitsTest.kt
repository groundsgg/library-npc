package gg.grounds.npc.importer

import gg.grounds.npc.importer.config.*
import gg.grounds.npc.importer.error.NpcImportException
import gg.grounds.npc.importer.internal.ImportBudget
import gg.grounds.npc.importer.internal.blockbench.BlockbenchReader
import gg.grounds.npc.importer.internal.bundle.NpcCompilation
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImportLimitsTest {
    @TempDir lateinit var temp: Path

    @Test
    fun stopsReadingLocalTexturesWhenTheirSharedBudgetIsExhausted() {
        val json = BlockbenchFixture.model()
        val first = json.getAsJsonArray("textures")[0].asJsonObject
        first.remove("source")
        first.addProperty("relative_path", "first.png")
        val second = first.deepCopy()
        second.addProperty("relative_path", "never-read.png")
        json.getAsJsonArray("textures").add(second)
        Files.write(temp.resolve("first.png"), BlockbenchFixture.png())
        val source = BlockbenchFixture.write(temp.resolve("cube.bbmodel"), json)
        val error =
            assertThrows(NpcImportException::class.java) {
                BlockbenchReader.read(
                    source,
                    "demo:npc/cube",
                    temp,
                    ImportBudget(BlockbenchFixture.png().size, 8192),
                )
            }
        assertEquals("NPC_IMPORT_LIMIT", error.problems.first().code)
    }

    @Test
    fun chargesServerModelsBeforeReadingTheNextSource() {
        val input = temp.resolve("models")
        BlockbenchFixture.write(input.resolve("a.bbmodel"))
        Files.writeString(input.resolve("b.bbmodel"), "invalid later source")
        val options =
            NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3).build()
        val error =
            assertThrows(NpcImportException::class.java) {
                NpcCompilation.prepare(options, serverBudget = ImportBudget(1, 8192))
            }
        assertEquals("NPC_IMPORT_LIMIT", error.problems.first().code)
    }

    @Test
    fun chargesGeneratedClientEntriesDuringSingleModelImport() {
        val source = BlockbenchFixture.write(temp.resolve("cube.bbmodel"))
        val error =
            assertThrows(NpcImportException::class.java) {
                BlockbenchReader.read(
                    source,
                    "demo:npc/cube",
                    temp,
                    ImportBudget(256 * 1024 * 1024, 1),
                )
            }
        assertEquals("NPC_IMPORT_LIMIT", error.problems.first().code)
    }
}
