package gg.grounds.npc.importer.internal.blockbench

import com.google.gson.*
import gg.grounds.npc.importer.internal.*

internal class BlockbenchGeometry(
    private val c: ImportContext,
    private val id: String,
    private val root: JsonObject,
    private val textures: List<String>,
    private val files: MutableMap<String, ByteArray>,
) {
    val bones = JsonArray()
    val parts = JsonArray()
    val halfSizes = mutableListOf<List<Double>>()
    private val hitboxes = mutableListOf<Pair<List<Double>, List<Double>>>()
    private val seenElements = hashSetOf<String>()
    private val seenBones = hashSetOf<String>()
    private val elements = linkedMapOf<String, JsonObject>()
    private val groups = linkedMapOf<String, JsonObject>()
    private val namespace = id.substringBefore(':')
    private val suffix = id.substringAfter(':').removePrefix("npc/")

    fun read() {
        for ((i, raw) in root.getAsJsonArray("elements").withIndex()) c.at("$.elements[$i]") {
            val element = raw.asJsonObject
            require(elements.put(element["uuid"].asString, element) == null) {
                "Duplicate element UUID"
            }
        }
        require(elements.size <= 4096) { "Too many source elements" }
        root.getAsJsonArray("groups")?.forEach {
            val group = it.asJsonObject
            require(groups.put(group["uuid"].asString, group) == null) { "Duplicate group UUID" }
        }
        val outliner = root.getAsJsonArray("outliner")
        fun collectLegacy(list: JsonArray, depth: Int) {
            require(depth <= 256) { "Hierarchy is too deep" }
            for (item in list) if (item.isJsonObject) {
                val group = item.asJsonObject
                if (group.has("name"))
                    require(groups.put(group["uuid"].asString, group) == null) {
                        "Duplicate group UUID"
                    }
                collectLegacy(group.getAsJsonArray("children") ?: JsonArray(), depth + 1)
            }
        }
        if (root.getAsJsonObject("meta")["format_version"].asString == "4.10")
            collectLegacy(outliner, 0)
        fun walk(list: JsonArray, parent: String?, hidden: Boolean, hitbox: Boolean, depth: Int) {
            require(depth <= 256) { "Hierarchy is too deep" }
            for ((index, item) in list.withIndex()) c.at("$.outliner[$index]") {
                if (item.isJsonPrimitive) {
                    val key = item.asString
                    require(seenElements.add(key)) { "Duplicate element in hierarchy" }
                    val element = elements[key] ?: error("Unknown element $key")
                    if (element["export"]?.asBoolean != false && (!hidden || hitbox))
                        cube(element, parent, hitbox)
                } else {
                    val node = item.asJsonObject
                    val key = node["uuid"].asString
                    require(seenBones.add(key)) { "Duplicate/cyclic bone" }
                    val group = groups[key] ?: error("Unknown group $key")
                    require(seenBones.size <= 256) { "Too many bones" }
                    require(
                        c.vector(group["scale"], "$.groups.$key.scale", listOf(1.0, 1.0, 1.0)) ==
                            listOf(1.0, 1.0, 1.0)
                    ) {
                        "Static bone scale is unsupported"
                    }
                    bones.add(
                        ImportJson.objectOf(
                            "id" to key,
                            "name" to (group["name"]?.asString ?: key),
                            "parent" to parent,
                            "origin" to
                                c.vector(group["origin"], "$.groups.$key.origin", divisor = 16.0),
                            "rotation" to c.vector(group["rotation"], "$.groups.$key.rotation"),
                        )
                    )
                    walk(
                        node.getAsJsonArray("children") ?: JsonArray(),
                        key,
                        hidden || group["export"]?.asBoolean == false,
                        hitbox || group["name"]?.asString?.lowercase() == "hitbox",
                        depth + 1,
                    )
                }
            }
        }
        walk(outliner, null, false, false, 0)
        require(seenElements == elements.keys) { "Unreferenced source element" }
        require(parts.size() in 1..1024) { "Expected 1..1024 visible parts" }
    }

    fun authoredBounds(): JsonObject? {
        if (hitboxes.isEmpty()) return null
        val lo = (0..2).map { axis -> hitboxes.minOf { it.first[axis] } }
        val hi = (0..2).map { axis -> hitboxes.maxOf { it.second[axis] } }
        return ImportJson.objectOf(
            "center" to (0..2).map { (lo[it] + hi[it]) / 32 },
            "size" to (0..2).map { (hi[it] - lo[it]) / 16 },
        )
    }

    private fun cube(e: JsonObject, parent: String?, hitbox: Boolean) =
        c.at("$.elements.${e["uuid"].asString}") {
            require((e["type"]?.asString ?: "cube") == "cube") { "Only cube geometry is supported" }
            require(e["box_uv"]?.asBoolean != true) {
                "Box UV is unsupported; export explicit face UVs"
            }
            val from = c.vector(e["from"], "$.elements.from")
            val to = c.vector(e["to"], "$.elements.to")
            val lo = (0..2).map { minOf(from[it], to[it]) }.toMutableList()
            val hi = (0..2).map { maxOf(from[it], to[it]) }.toMutableList()
            if (hitbox) {
                hitboxes.add(lo to hi)
                return@at
            }
            val inflate = c.number(e["inflate"], "$.elements.inflate", 0.0)
            for (axis in 0..2) {
                lo[axis] -= inflate
                hi[axis] += inflate
            }
            val sizes = (0..2).map { hi[it] - lo[it] }
            require(sizes.all { it in 0.0..48.0 }) { "Cube size exceeds native model range" }
            val resolution = root.getAsJsonObject("resolution")
            val width = c.number(resolution["width"], "$.resolution.width")
            val height = c.number(resolution["height"], "$.resolution.height")
            require(width > 0 && height > 0) { "Invalid UV resolution" }
            val faces = JsonObject()
            for ((name, raw) in e.getAsJsonObject("faces").entrySet()) {
                require(name in setOf("north", "south", "east", "west", "up", "down")) {
                    "Unknown face"
                }
                val face = raw.asJsonObject
                if (face["texture"] == null || face["texture"].isJsonNull) continue
                val index = face["texture"].asBigDecimal.intValueExact()
                require(index in textures.indices) { "Unknown texture index" }
                val values = face.getAsJsonArray("uv")
                require(values.size() == 4) { "Invalid face UV" }
                val uv = values.mapIndexed { i, v ->
                    c.number(v, "$.elements.faces.$name.uv[$i]") * 16 /
                        if (i % 2 == 0) width else height
                }
                val rotation = face["rotation"]?.asInt ?: 0
                require(rotation in listOf(0, 90, 180, 270)) { "Unsupported UV rotation" }
                faces.add(
                    name,
                    ImportJson.objectOf("uv" to uv, "texture" to "#$index").apply {
                        if (rotation != 0) addProperty("rotation", rotation)
                    },
                )
            }
            require(faces.size() > 0) { "Visible cube has no textured faces" }
            val native =
                ImportJson.objectOf(
                    "textures" to
                        JsonObject().apply {
                            textures.forEachIndexed { i, value -> addProperty(i.toString(), value) }
                            addProperty(
                                "particle",
                                textures[
                                    faces
                                        .entrySet()
                                        .first()
                                        .value
                                        .asJsonObject["texture"]
                                        .asString
                                        .removePrefix("#")
                                        .toInt()],
                            )
                        },
                    "elements" to
                        listOf(
                            ImportJson.objectOf(
                                    "from" to sizes.map { 8 - it / 2 },
                                    "to" to sizes.map { 8 + it / 2 },
                                    "faces" to faces,
                                )
                                .apply { e["shade"]?.let { add("shade", it) } }
                        ),
                )
            val bytes = ImportJson.bytes(native)
            val path =
                "npc_parts/$suffix/part_${parts.size().toString().padStart(3,'0')}_${ImportJson.hash(bytes)}"
            files["assets/$namespace/models/$path.json"] = bytes
            files["assets/$namespace/items/$path.json"] =
                ImportJson.bytes(
                    ImportJson.objectOf(
                        "model" to
                            ImportJson.objectOf(
                                "type" to "minecraft:model",
                                "model" to "$namespace:$path",
                            )
                    )
                )
            parts.add(
                ImportJson.objectOf(
                    "bone" to parent,
                    "model" to "$namespace:$path",
                    "center" to (0..2).map { (lo[it] + hi[it]) / 32 },
                    "pivot" to c.vector(e["origin"], "$.elements.origin", divisor = 16.0),
                    "rotation" to c.vector(e["rotation"], "$.elements.rotation"),
                    "scale" to (0..2).map { if (from[it] > to[it]) -1.0 else 1.0 },
                )
            )
            halfSizes.add(sizes.map { it / 32 })
        }
}
