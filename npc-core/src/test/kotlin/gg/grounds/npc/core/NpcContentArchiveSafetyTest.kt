package gg.grounds.npc.core

import gg.grounds.npc.core.internal.content.ContentJson
import gg.grounds.npc.core.io.content.NpcContentException
import gg.grounds.npc.core.io.content.NpcResourceArchive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcContentArchiveSafetyTest {
    @TempDir lateinit var temp: Path

    @Test
    fun rejectsCentralDirectoryEntriesHiddenByAForgedCount() {
        val path = temp.resolve("hidden-link.zip")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            for (name in listOf("first", "link")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write("payload".toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = Files.readAllBytes(path)
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val records = (0..bytes.size - 4).filter { view.getInt(it) == 0x02014b50 }
        assertEquals(2, records.size)
        view.putInt(records[1] + 38, 0xa1ff0000L.toInt())
        view.putShort(bytes.size - 22 + 8, 1.toShort())
        view.putShort(bytes.size - 22 + 10, 1.toShort())
        Files.write(path, bytes)
        assertThrows(NpcContentException::class.java) { NpcResourceArchive.read(path) }
    }

    @Test
    fun metadataArchiveAndSnapshotEnforceTheirOwnLimitBeforeCopying() {
        val bytes = ByteArray(4 * 1024 * 1024 + 1) { 32 }
        val path = temp.resolve("large-metadata.zip")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("pack.mcmeta"))
            zip.write(bytes)
            zip.closeEntry()
        }
        assertThrows(NpcContentException::class.java) { NpcResourceArchive.read(path) }
        assertThrows(NpcContentException::class.java) {
            gg.grounds.npc.core.internal.content.ContentFiles.snapshot(
                mapOf("assets/demo/sounds.json" to bytes),
                256 * 1024 * 1024,
            )
        }
    }

    @Test
    fun metadataBytesAreRejectedBeforeUtf8Decoding() {
        val bytes = ByteArray(4 * 1024 * 1024 + 1) { 0xff.toByte() }
        val error =
            assertThrows(NpcContentException::class.java) {
                ContentJson.parse(bytes, "pack.mcmeta")
            }
        assertEquals("NPC_CONTENT_LIMIT", error.problems.first().code)
    }
}
