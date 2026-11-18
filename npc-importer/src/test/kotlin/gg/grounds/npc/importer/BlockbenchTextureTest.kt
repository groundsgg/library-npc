package gg.grounds.npc.importer

import com.google.gson.*
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BlockbenchTextureTest {
    @TempDir lateinit var temp: Path

    @Test
    fun validationDoesNotCreateAnImageIoDiskCache() {
        val source = BlockbenchFixture.write(temp.resolve("cube.bbmodel"))
        val directory = Files.createDirectory(temp.resolve("image-cache"))
        val previousDirectory = javax.imageio.ImageIO.getCacheDirectory()
        val previousUseCache = javax.imageio.ImageIO.getUseCache()
        java.nio.file.FileSystems.getDefault().newWatchService().use { watcher ->
            directory.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE)
            javax.imageio.ImageIO.setCacheDirectory(directory.toFile())
            javax.imageio.ImageIO.setUseCache(true)
            try {
                NpcBlockbenchImporter.importModel(
                    source,
                    "demo:npc/cube",
                )
                assertNull(
                    watcher.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "Model validation must not create disk cache files",
                )
            } finally {
                javax.imageio.ImageIO.setCacheDirectory(previousDirectory)
                javax.imageio.ImageIO.setUseCache(previousUseCache)
            }
        }
    }

    @Test
    fun readsRelativePngAndProducesEquivalentResources() {
        val embedded = BlockbenchFixture.model()
        val relative = embedded.deepCopy()
        val texture = relative.getAsJsonArray("textures")[0].asJsonObject
        texture.remove("source")
        texture.addProperty("relative_path", "texture.png")
        Files.write(temp.resolve("texture.png"), BlockbenchFixture.png())
        val a =
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("a.bbmodel"), embedded),
                "demo:npc/cube",
            )
        val b =
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("b.bbmodel"), relative),
                "demo:npc/cube",
            )
        assertEquals(a.resources.keys, b.resources.keys)
        for (key in a.resources.keys) assertArrayEquals(
            a.resources.getValue(key),
            b.resources.getValue(key),
        )
    }

    @Test
    fun rejectsMissingInvalidAndEscapingTextures() {
        for (source in
            listOf("data:image/png;base64,AA==", "https://example.invalid/texture.png")) {
            val json = BlockbenchFixture.model()
            json.getAsJsonArray("textures")[0].asJsonObject.addProperty("source", source)
            assertThrows(NpcImportException::class.java) {
                NpcBlockbenchImporter.importModel(
                    BlockbenchFixture.write(temp.resolve("bad.bbmodel"), json),
                    "demo:npc/cube",
                )
            }
        }
        val json = BlockbenchFixture.model()
        val texture = json.getAsJsonArray("textures")[0].asJsonObject
        texture.remove("source")
        texture.addProperty("relative_path", "../outside.png")
        assertThrows(NpcImportException::class.java) {
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("escape.bbmodel"), json),
                "demo:npc/cube",
            )
        }
    }
}
