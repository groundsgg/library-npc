package gg.grounds.npc.importer

import gg.grounds.npc.importer.config.*
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcReproducibilityTest {
    @TempDir lateinit var temp: Path

    @Test
    fun producesIdenticalBytesAfterMovingSourcesAndChangingCreationOrder() {
        val a = temp.resolve("a")
        val b = temp.resolve("different/b")
        for (name in listOf("first", "second")) BlockbenchFixture.write(a.resolve("$name.bbmodel"))
        for (name in listOf("second", "first")) BlockbenchFixture.write(b.resolve("$name.bbmodel"))
        fun compile(input: Path, name: String) =
            NpcModelImporter.compile(
                NpcImportOptions.builder(input, "demo", NpcMinecraftTarget.MINECRAFT_26_3).build(),
                temp.resolve(name),
            )
        val x = compile(a, "out-a")
        val y = compile(b, "out-b")
        assertEquals(x.contentId, y.contentId)
        assertArrayEquals(Files.readAllBytes(x.serverContent), Files.readAllBytes(y.serverContent))
        assertArrayEquals(Files.readAllBytes(x.resourcePack), Files.readAllBytes(y.resourcePack))
        assertArrayEquals(Files.readAllBytes(x.manifest), Files.readAllBytes(y.manifest))
    }
}
