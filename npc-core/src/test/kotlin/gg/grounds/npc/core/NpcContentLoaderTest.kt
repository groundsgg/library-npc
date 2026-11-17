package gg.grounds.npc.core

import com.google.gson.*
import gg.grounds.npc.core.io.content.NpcContentException
import gg.grounds.npc.core.io.content.NpcContentLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcContentLoaderTest {
    @TempDir lateinit var temp: Path

    @Test
    fun loadsZipAndDirectoryWithImmutableModelSet() {
        val fixture = ContentFixture()
        val directory = fixture.directory(temp.resolve("content"))
        val archive = fixture.zip(temp.resolve("content.zip"))
        for (source in listOf(directory, archive)) {
            val content = NpcContentLoader.load(source)
            assertEquals(setOf("demo:npc/cube"), content.models.keys)
            assertEquals(fixture.contract["contentId"].asString, content.contentId)
            assertEquals("26.3", content.target)
            assertThrows(UnsupportedOperationException::class.java) {
                (content.models as MutableMap<String, gg.grounds.npc.core.model.NpcModel>).clear()
            }
        }
    }

    @Test
    fun rejectsChangedModelBytesBeforeReturningAnyModels() {
        val fixture = ContentFixture()
        fixture.files["models/cube.json"] = "{}".toByteArray()
        rejected(fixture, "NPC_CONTENT_HASH")
    }

    @Test
    fun rejectsForgedContentIdentity() {
        val fixture = ContentFixture()
        fixture.contract.addProperty("contentId", "0".repeat(64))
        rejected(fixture, "NPC_CONTENT_ID")
    }

    @Test
    fun rejectsModelIndexIdentityMismatch() {
        val fixture = ContentFixture()
        fixture.contract
            .getAsJsonArray("models")[0]
            .asJsonObject
            .addProperty("id", "demo:npc/other")
        fixture.identify()
        rejected(fixture, "NPC_CONTENT_MODEL_ID")
    }

    @Test
    fun rejectsDuplicateModelIdsAndResourcePaths() {
        val fixture = ContentFixture()
        fixture.contract
            .getAsJsonArray("models")
            .add(fixture.contract.getAsJsonArray("models")[0].deepCopy())
        fixture.identify()
        rejected(fixture, "NPC_CONTENT_DUPLICATE")
    }

    @Test
    fun rejectsUnknownSchemaAndTarget() {
        for ((field, value) in
            listOf("bundleSchema" to JsonPrimitive(99), "target" to JsonPrimitive("99.0"))) {
            val fixture = ContentFixture()
            fixture.contract.add(field, value)
            fixture.identify()
            rejected(fixture, "NPC_CONTENT_CONTRACT")
        }
    }

    @Test
    fun rejectsResourceTraversalAndUnknownPayload() {
        val fixture = ContentFixture()
        fixture.files["../escape"] = byteArrayOf(1)
        rejected(fixture, "NPC_CONTENT_PATH")
    }

    @Test
    fun rejectsSymlinkResources() {
        val fixture = ContentFixture()
        val dir = fixture.directory(temp.resolve("symlink"))
        Files.createSymbolicLink(dir.resolve("link"), temp)
        assertEquals(
            "NPC_CONTENT_PATH",
            assertThrows(NpcContentException::class.java) { NpcContentLoader.load(dir) }
                .problems
                .first()
                .code,
        )
    }

    @Test
    fun rejectsInvalidUtf8ContractAndDuplicateJsonKeys() {
        for (bytes in
            listOf(
                byteArrayOf(0xc3.toByte(), 0x28),
                "{\"bundleSchema\":1,\"bundleSchema\":2}".toByteArray(),
            )) {
            val f = ContentFixture()
            val dir = f.directory(temp.resolve("invalid-${bytes.size}"))
            Files.write(dir.resolve("bundle.json"), bytes)
            assertThrows(NpcContentException::class.java) { NpcContentLoader.load(dir) }
        }
    }

    @Test
    fun rejectsExpandedPayloadBeyondPerModelBudget() {
        val f = ContentFixture()
        f.files["models/cube.json"] = ByteArray(2 * 1024 * 1024 + 1) { 32 }
        f.contract
            .getAsJsonArray("models")[0]
            .asJsonObject
            .addProperty("sha256", ContentFixture.sha(f.files.getValue("models/cube.json")))
        f.identify()
        rejected(f, "NPC_CONTENT_LIMIT")
    }

    private fun rejected(fixture: ContentFixture, code: String) {
        val error =
            assertThrows(NpcContentException::class.java) {
                NpcContentLoader.load(fixture.zip(temp.resolve("invalid.zip")))
            }
        assertEquals(code, error.problems.first().code)
    }
}

internal class ContentFixture {
    val client = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:part\"}}".toByteArray()
    val sound = JsonParser.parseString("{\"sounds\":[\"demo:wave\"]}")
    val files =
        linkedMapOf(
            "models/cube.json" to
                """{"schemaVersion":1,"asset":"demo:npc/cube","bounds":{"center":[0,0,0],"size":[1,1,1]},"bones":[],"parts":[{"bone":null,"model":"demo:part","center":[0,0,0],"pivot":[0,0,0],"rotation":[0,0,0]}],"animations":{},"sounds":{}}"""
                    .toByteArray()
        )
    val contract =
        JsonObject().apply {
            addProperty("bundleSchema", 1)
            addProperty("compilerVersion", "test")
            addProperty("target", "26.3")
            add(
                "models",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "demo:npc/cube")
                            addProperty("resource", "models/cube.json")
                            addProperty("sha256", sha(files.getValue("models/cube.json")))
                        }
                    )
                },
            )
            add(
                "clientFiles",
                JsonObject().apply { addProperty("assets/demo/items/part.json", sha(client)) },
            )
            add("soundEvents", JsonObject().apply { add("demo:wave", sound) })
        }

    init {
        identify()
    }

    fun identify() {
        contract.remove("contentId")
        contract.addProperty("contentId", sha(canonical(contract).toByteArray()))
    }

    fun directory(path: Path): Path {
        for ((name, bytes) in files + ("bundle.json" to contract.toString().toByteArray())) {
            val destination = path.resolve(name)
            Files.createDirectories(destination.parent)
            Files.write(destination, bytes)
        }
        return path
    }

    fun zip(path: Path): Path =
        zipFiles(path, files + ("bundle.json" to contract.toString().toByteArray()))

    companion object {
        fun sha(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        fun canonical(v: JsonElement): String =
            when {
                v.isJsonObject ->
                    v.asJsonObject.keySet().sorted().joinToString(",", "{", "}") {
                        JsonPrimitive(it).toString() + ":" + canonical(v.asJsonObject[it])
                    }
                v.isJsonArray -> v.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
                else -> v.toString()
            }

        fun zipFiles(path: Path, entries: Map<String, ByteArray>): Path {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return path
        }
    }
}
