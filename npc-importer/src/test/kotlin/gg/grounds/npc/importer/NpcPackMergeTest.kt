package gg.grounds.npc.importer

import com.google.gson.JsonParser
import gg.grounds.npc.core.io.content.NpcContentLoader
import gg.grounds.npc.importer.config.*
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.*
import java.util.zip.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcPackMergeTest {
    @TempDir lateinit var temp: Path

    private fun options(base: Path? = null) =
        NpcImportOptions.builder(
                BlockbenchFixture.write(temp.resolve("cube.bbmodel")),
                "demo",
                NpcMinecraftTarget.MINECRAFT_26_3,
            )
            .apply { if (base != null) basePack(base) }
            .build()

    @Test
    fun readsMajorOnlyUpperBoundAndNormalizesLegacyCompatibilityFieldsForTheTarget() {
        val metadata =
            """{"pack":{"pack_format":60,"supported_formats":[60,97],"min_format":60,"max_format":97,"description":"Existing"},"overlays":{"entries":[{"formats":{"min_inclusive":60,"max_inclusive":97},"min_format":60,"max_format":97,"directory":"foreign"}]}}"""
        val base =
            PackFixture.zip(
                temp.resolve("base.zip"),
                mapOf(
                    "pack.mcmeta" to metadata.toByteArray(),
                    "foreign/assets/other/keep.txt" to byteArrayOf(1),
                ),
            )
        val output = NpcModelImporter.compile(options(base), temp.resolve("output"))
        NpcContentLoader.load(output.serverContent).verifyResourcePack(output.resourcePack)
        val result =
            JsonParser.parseString(
                    String(PackFixture.read(output.resourcePack).getValue("pack.mcmeta"))
                )
                .asJsonObject
        assertFalse(result.getAsJsonObject("pack").has("supported_formats"))
        assertFalse(
            result
                .getAsJsonObject("overlays")
                .getAsJsonArray("entries")[0]
                .asJsonObject
                .has("formats")
        )
        assertEquals("[97,1]", result.getAsJsonObject("pack")["max_format"].toString())
    }

    @Test
    fun mergesZipAndDirectoryPreservingForeignBytesAndDescription() {
        val files =
            mapOf(
                "pack.mcmeta" to PackFixture.metadata(),
                "pack.png" to byteArrayOf(1, 2, 3),
                "assets/foreign/items/keep.json" to "foreign bytes".toByteArray(),
            )
        val directory = temp.resolve("base")
        for ((name, bytes) in files) {
            val p = directory.resolve(name)
            Files.createDirectories(p.parent)
            Files.write(p, bytes)
        }
        val zip = PackFixture.zip(temp.resolve("base.zip"), files)
        for ((i, base) in listOf(directory, zip).withIndex()) {
            val output = NpcModelImporter.compile(options(base), temp.resolve("output-$i"))
            ZipFile(output.resourcePack.toFile()).use { pack ->
                assertArrayEquals(
                    files.getValue("pack.png"),
                    pack.getInputStream(pack.getEntry("pack.png")).readAllBytes(),
                )
                assertArrayEquals(
                    files.getValue("assets/foreign/items/keep.json"),
                    pack
                        .getInputStream(pack.getEntry("assets/foreign/items/keep.json"))
                        .readAllBytes(),
                )
                assertEquals(
                    "Existing",
                    JsonParser.parseString(
                            pack.getInputStream(pack.getEntry("pack.mcmeta")).reader().readText()
                        )
                        .asJsonObject
                        .getAsJsonObject("pack")["description"]
                        .asString,
                )
            }
            NpcContentLoader.load(output.serverContent).verifyResourcePack(output.resourcePack)
        }
    }

    @Test
    fun deduplicatesIdenticalFilesAndRejectsDifferentFilesAtTheSamePath() {
        val original = NpcModelImporter.compile(options(), temp.resolve("first"))
        val originalFiles = PackFixture.read(original.resourcePack)
        val same = PackFixture.zip(temp.resolve("same.zip"), originalFiles)
        NpcModelImporter.compile(options(same), temp.resolve("same-result"))
        val key = originalFiles.keys.first { it.contains("/models/") }
        val conflict =
            PackFixture.zip(
                temp.resolve("conflict.zip"),
                originalFiles + (key to "{}".toByteArray()),
            )
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(conflict), temp.resolve("conflict-result"))
        }
        assertFalse(Files.exists(temp.resolve("conflict-result")))
    }

    @Test
    fun rejectsWrongTargetTraversalAndCaseAmbiguousZipEntries() {
        val cases =
            listOf(
                mapOf("pack.mcmeta" to PackFixture.metadata(88)),
                mapOf("pack.mcmeta" to PackFixture.metadata(), "../escape" to byteArrayOf(1)),
                mapOf(
                    "pack.mcmeta" to PackFixture.metadata(),
                    "assets/demo/a" to byteArrayOf(1),
                    "assets/demo/A" to byteArrayOf(2),
                ),
            )
        for ((i, files) in cases.withIndex()) {
            val base = PackFixture.zip(temp.resolve("bad-$i.zip"), files)
            assertThrows(NpcImportException::class.java) {
                NpcModelImporter.compile(options(base), temp.resolve("bad-result-$i"))
            }
        }
    }

    @Test
    fun rejectsAnActiveOverlayOrFilterThatChangesOwnAssetMeaning() {
        val original = NpcModelImporter.compile(options(), temp.resolve("first"))
        val key = PackFixture.read(original.resourcePack).keys.first { it.contains("/items/") }
        val metadata = JsonParser.parseString(String(PackFixture.metadata())).asJsonObject
        metadata.add(
            "overlays",
            JsonParser.parseString(
                """{"entries":[{"min_format":97,"max_format":97,"directory":"overlay"}]}"""
            ),
        )
        val base =
            PackFixture.zip(
                temp.resolve("overlay.zip"),
                mapOf(
                    "pack.mcmeta" to metadata.toString().toByteArray(),
                    "overlay/$key" to "{}".toByteArray(),
                ),
            )
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(base), temp.resolve("overlay-result"))
        }
        metadata.remove("overlays")
        metadata.add(
            "filter",
            JsonParser.parseString("""{"block":[{"namespace":"demo","path":".*"}]}"""),
        )
        val filtered =
            PackFixture.zip(
                temp.resolve("filter.zip"),
                mapOf("pack.mcmeta" to metadata.toString().toByteArray()),
            )
        assertThrows(NpcImportException::class.java) {
            NpcModelImporter.compile(options(filtered), temp.resolve("filter-result"))
        }
    }
}

internal object PackFixture {
    fun metadata(major: Int = 97) =
        """{"pack":{"pack_format":$major,"min_format":[$major,${if(major==97)1 else 0}],"max_format":[$major,${if(major==97)1 else 0}],"description":"Existing"}}"""
            .toByteArray()

    fun zip(path: Path, entries: Map<String, ByteArray>): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return path
    }

    fun read(path: Path): Map<String, ByteArray> =
        ZipFile(path.toFile()).use { z ->
            z.entries()
                .asSequence()
                .filterNot { it.isDirectory }
                .associate { it.name to z.getInputStream(it).use { s -> s.readAllBytes() } }
        }
}
