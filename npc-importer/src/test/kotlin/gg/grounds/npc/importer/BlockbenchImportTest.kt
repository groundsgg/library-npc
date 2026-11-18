package gg.grounds.npc.importer

import com.google.gson.*
import gg.grounds.npc.core.animation.NpcPoseSampler
import gg.grounds.npc.importer.error.NpcImportException
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BlockbenchImportTest {
    @TempDir lateinit var temp: Path

    @Test
    fun importsAnOriginalGenericCubeWithValidatedRuntimeAndClientAssets() {
        val source = BlockbenchFixture.write(temp.resolve("cube.bbmodel"))
        val imported = NpcBlockbenchImporter.importModel(source, "demo:npc/cube")
        assertEquals("demo:npc/cube", imported.model.id)
        assertEquals(1, imported.model.parts.size)
        assertEquals(1.0, imported.model.bounds.size.x)
        assertEquals(0.5, imported.model.bounds.center.y)
        assertEquals(1, imported.resources.keys.count { it.endsWith(".png") })
        val part = imported.model.parts.single()
        val item = "assets/demo/items/${part.model.substringAfter(':')}.json"
        assertNotNull(imported.resources[item])
        val png = imported.resources.entries.single { it.key.endsWith(".png") }
        png.value[0] = 0
        assertEquals(0x89.toByte(), imported.resources.getValue(png.key)[0])
    }

    @Test
    fun acceptsResolvedFaceUvsOnIndividuallyBoxMarkedCubes() {
        val json = BlockbenchFixture.model()
        val cube = json.getAsJsonArray("elements")[0].asJsonObject
        cube.addProperty("box_uv", true)
        val imported =
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("resolved-box.bbmodel"), json),
                "demo:npc/cube",
            )
        val native =
            JsonParser.parseString(
                    imported.resources.entries
                        .single {
                            it.key.startsWith("assets/demo/models/")
                        }
                        .value
                        .toString(Charsets.UTF_8)
                )
                .asJsonObject
        assertEquals(
            JsonParser.parseString("[0,0,16,16]"),
            native
                .getAsJsonArray("elements")[0]
                .asJsonObject
                .getAsJsonObject("faces")
                .getAsJsonObject("north")["uv"],
        )
        cube.getAsJsonObject("faces").getAsJsonObject("north").remove("uv")
        assertThrows(NpcImportException::class.java) {
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("missing-uv.bbmodel"), json),
                "demo:npc/cube",
            )
        }
    }

    @Test
    fun acceptsProjectBoxUvWhenFacesAreAlreadyResolved() {
        val json = BlockbenchFixture.model()
        json.getAsJsonObject("meta").addProperty("box_uv", true)
        val cube = json.getAsJsonArray("elements")[0].asJsonObject
        cube.addProperty("box_uv", true)
        val imported =
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("box-project.bbmodel"), json),
                "demo:npc/cube",
            )
        assertEquals(1, imported.model.parts.size)
        cube.getAsJsonObject("faces").getAsJsonObject("north").remove("uv")
        assertThrows(NpcImportException::class.java) {
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("missing-box-uv.bbmodel"), json),
                "demo:npc/cube",
            )
        }
    }

    @Test
    fun authoredHitboxesApplyInflationAndRejectUnsupportedRotations() {
        val json = BlockbenchFixture.model()
        val cube = json.getAsJsonArray("elements")[0].asJsonObject.deepCopy()
        cube.addProperty("uuid", "hit")
        cube.add("from", JsonParser.parseString("[16,0,0]"))
        cube.add("to", JsonParser.parseString("[32,16,16]"))
        cube.addProperty("inflate", 1)
        json.getAsJsonArray("elements").add(cube)
        json
            .getAsJsonArray("groups")
            .add(
                JsonParser.parseString(
                    "{\"uuid\":\"hitbox\",\"name\":\"hitbox\",\"origin\":[0,0,0]}"
                )
            )
        json
            .getAsJsonArray("outliner")[0]
            .asJsonObject
            .getAsJsonArray("children")
            .add(JsonParser.parseString("{\"uuid\":\"hitbox\",\"children\":[\"hit\"]}"))
        fun import() =
            NpcBlockbenchImporter.importModel(
                BlockbenchFixture.write(temp.resolve("hitbox.bbmodel"), json),
                "demo:npc/cube",
            )
        assertEquals(1.125, import().model.bounds.size.x)
        for (target in listOf(cube, json.getAsJsonArray("groups")[0].asJsonObject)) {
            target.add("rotation", JsonParser.parseString("[0,0,90]"))
            val error = assertThrows(NpcImportException::class.java) { import() }
            assertTrue(error.problems.first().path.contains("rotation"))
            target.remove("rotation")
        }
    }

    @Test
    fun preservesAuthoredPivotAndReflectedScale() {
        val json = BlockbenchFixture.model()
        val cube = json.getAsJsonArray("elements")[0].asJsonObject
        cube.add("from", JsonParser.parseString("[16,0,0]"))
        cube.add("to", JsonParser.parseString("[0,16,16]"))
        cube.add("origin", JsonParser.parseString("[8,8,8]"))
        val model =
            NpcBlockbenchImporter.importModel(
                    BlockbenchFixture.write(temp.resolve("reflected.bbmodel"), json),
                    "demo:npc/reflected",
                )
                .model
        assertEquals(-1.0, model.parts.single().scale.x)
        assertEquals(0.5, model.parts.single().pivot.x)
        assertEquals(-1.0, NpcPoseSampler.sample(model, null, 0.0).parts.single()[0])
    }

    @Test
    fun rejectsUnsupportedGeometryVersionAndBrokenHierarchy() {
        val modifications =
            listOf<(JsonObject) -> Unit>(
                { it.getAsJsonArray("elements")[0].asJsonObject.addProperty("type", "mesh") },
                { it.getAsJsonObject("meta").addProperty("format_version", "99.0") },
                {
                    it.getAsJsonArray("outliner")[0]
                        .asJsonObject
                        .getAsJsonArray("children")
                        .add("missing")
                },
            )
        for ((i, change) in modifications.withIndex()) {
            val data = BlockbenchFixture.model()
            change(data)
            val file = BlockbenchFixture.write(temp.resolve("invalid-$i.bbmodel"), data)
            val error =
                assertThrows(NpcImportException::class.java) {
                    NpcBlockbenchImporter.importModel(file, "demo:npc/cube")
                }
            assertTrue(error.problems.first().source.endsWith("invalid-$i.bbmodel"))
            assertTrue(error.problems.first().path.startsWith("$"))
        }
    }
}

internal object BlockbenchFixture {
    fun png(): ByteArray {
        val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xffff0000.toInt())
        return java.io
            .ByteArrayOutputStream()
            .also { javax.imageio.ImageIO.write(image, "png", it) }
            .toByteArray()
    }

    fun model(): JsonObject =
        JsonParser.parseString(
                """{
        "meta":{"format_version":"5.0","model_format":"free"},
        "resolution":{"width":2,"height":2},
        "textures":[],
        "elements":[{"uuid":"cube","name":"Cube","type":"cube","from":[0,0,0],"to":[16,16,16],"origin":[0,0,0],"faces":{"north":{"texture":0,"uv":[0,0,2,2]}}}],
        "groups":[{"uuid":"bone","name":"body","origin":[0,0,0],"rotation":[0,0,0]}],
        "outliner":[{"uuid":"bone","children":["cube"]}],
        "animations":[]
    }"""
            )
            .asJsonObject
            .apply {
                getAsJsonArray("textures")
                    .add(
                        JsonObject().apply {
                            addProperty(
                                "source",
                                "data:image/png;base64," +
                                    java.util.Base64.getEncoder().encodeToString(png()),
                            )
                        }
                    )
            }

    fun animation(
        channel: String,
        last: List<Double>,
        interpolation: String = "linear",
    ): JsonObject =
        JsonObject().apply {
            addProperty("name", "move")
            addProperty("loop", "hold")
            addProperty("length", 1.0)
            add(
                "animators",
                JsonObject().apply {
                    add(
                        "bone",
                        JsonObject().apply {
                            addProperty("type", "bone")
                            add(
                                "keyframes",
                                JsonArray().apply {
                                    for ((i, v) in
                                        listOf(listOf(0.0, 0.0, 0.0), last).withIndex()) add(
                                        JsonObject().apply {
                                            addProperty("channel", channel)
                                            addProperty("time", i.toDouble())
                                            addProperty("interpolation", interpolation)
                                            add(
                                                "data_points",
                                                JsonArray().apply {
                                                    add(
                                                        JsonObject().apply {
                                                            for ((axis, value) in
                                                                listOf("x", "y", "z")
                                                                    .zip(v)) addProperty(
                                                                axis,
                                                                value,
                                                            )
                                                        }
                                                    )
                                                },
                                            )
                                        }
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

    fun write(path: Path, data: JsonObject = model()): Path {
        Files.createDirectories(path.parent)
        return Files.writeString(path, data.toString())
    }
}
