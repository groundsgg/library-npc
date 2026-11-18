package gg.grounds.npc.importer.internal.pack

import com.google.gson.*
import gg.grounds.npc.core.io.content.*
import gg.grounds.npc.importer.config.*
import gg.grounds.npc.importer.error.*
import gg.grounds.npc.importer.internal.*

internal data class ComposedPack(val files: Map<String, ByteArray>, val baseId: String?)

internal object PackComposer {
    fun compose(options: NpcImportOptions, generated: Map<String, ByteArray>): ComposedPack {
        val source = options.basePack?.toString() ?: "generated resourcepack"
        val context = ImportContext(source)
        val base =
            try {
                options.basePack?.let { NpcResourceArchive.read(it) } ?: emptyMap()
            } catch (e: NpcContentException) {
                throw NpcImportException(
                    e.problems.map { NpcImportProblem(it.code, source, it.path, it.message) }
                )
            }
        fun metadata(bytes: ByteArray): JsonObject {
            if (bytes.size > 4 * 1024 * 1024)
                fail(
                    source,
                    "pack.mcmeta/sounds.json",
                    "JSON resource exceeds 4 MiB",
                    "NPC_IMPORT_LIMIT",
                )
            return context.parse(bytes)
        }
        return context.at("pack.mcmeta") {
            val metadata =
                if (options.basePack == null) JsonObject()
                else metadata(base["pack.mcmeta"] ?: error("Base pack metadata is missing"))
            val pack =
                metadata.getAsJsonObject("pack") ?: JsonObject().also { metadata.add("pack", it) }
            val target = options.target
            fun version(raw: JsonElement, upper: Boolean = false): Pair<Int, Int> {
                fun integer(value: JsonElement): Int {
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                        "Pack versions must be integers"
                    }
                    return value.asBigDecimal.intValueExact().also { require(it >= 0) }
                }
                if (!raw.isJsonArray) return integer(raw) to if (upper) Int.MAX_VALUE else 0
                require(raw.asJsonArray.size() in 1..2) {
                    "Pack version must have one or two integer components"
                }
                val minor =
                    if (raw.asJsonArray.size() == 2) integer(raw.asJsonArray[1])
                    else if (upper) Int.MAX_VALUE else 0
                return integer(raw.asJsonArray[0]) to minor
            }
            fun less(a: Pair<Int, Int>, b: Pair<Int, Int>) =
                a.first < b.first || (a.first == b.first && a.second < b.second)
            if (options.basePack != null) {
                val low = version(pack["min_format"] ?: error("Missing required min_format"))
                val high = version(pack["max_format"] ?: error("Missing required max_format"), true)
                val selected = target.major to target.minor
                require(!less(selected, low) && !less(high, selected)) {
                    "Base pack does not support Minecraft ${target.version}"
                }
            }
            // Only the selected modern target is emitted; legacy range fields no longer apply.
            pack.remove("supported_formats")
            metadata.getAsJsonObject("overlays")?.getAsJsonArray("entries")?.let { entries ->
                val effective = JsonArray()
                val selected = target.major to target.minor
                for (raw in entries) {
                    val entry = raw.asJsonObject.deepCopy()
                    require(
                        entry["directory"].asString.matches(Regex("[A-Za-z0-9_.-]+")) &&
                            entry["directory"].asString !in setOf(".", "..")
                    ) {
                        "Invalid overlay directory"
                    }
                    val low = version(entry["min_format"] ?: error("Missing overlay min_format"))
                    val high =
                        version(entry["max_format"] ?: error("Missing overlay max_format"), true)
                    require(!less(high, low)) { "Inverted overlay range" }
                    if (!less(selected, low) && !less(high, selected)) {
                        entry.remove("formats")
                        entry.add(
                            "min_format",
                            ImportJson.gson.toJsonTree(listOf(target.major, target.minor)),
                        )
                        entry.add(
                            "max_format",
                            ImportJson.gson.toJsonTree(listOf(target.major, target.minor)),
                        )
                        effective.add(entry)
                    }
                }
                metadata.getAsJsonObject("overlays").add("entries", effective)
            }
            pack.addProperty("pack_format", target.major)
            pack.add("min_format", ImportJson.gson.toJsonTree(listOf(target.major, target.minor)))
            pack.add("max_format", ImportJson.gson.toJsonTree(listOf(target.major, target.minor)))
            if (options.description != null) pack.addProperty("description", options.description)
            else if (!pack.has("description")) pack.addProperty("description", "NPC models")
            val budget = ImportBudget()
            val result = linkedMapOf<String, ByteArray>()
            fun put(name: String, bytes: ByteArray) {
                budget.charge(name, bytes.size, source)
                result[name] = bytes
            }
            for ((name, bytes) in base) put(name, bytes)
            for ((name, bytes) in generated) {
                val old = result[name]
                if (old == null || old.contentEquals(bytes)) put(name, bytes)
                else if (name.matches(Regex("assets/[^/]+/sounds\\.json"))) {
                    val registry = metadata(old)
                    for ((key, definition) in metadata(bytes).entrySet()) {
                        require(!registry.has(key) || registry[key] == definition) {
                            "Conflicting sound event $name:$key"
                        }
                        registry.add(key, definition)
                    }
                    put(name, ImportJson.bytes(registry))
                } else
                    fail(
                        source,
                        name,
                        "Base and generated assets differ at the same path",
                        "NPC_IMPORT_COLLISION",
                    )
            }
            put("pack.mcmeta", ImportJson.bytes(metadata))
            val baseIdentity =
                if (options.basePack == null) null
                else
                    ImportJson.hash(
                        ImportJson.canonical(
                                ImportJson.gson.toJsonTree(
                                    base.mapValues { ImportJson.hash(it.value) }
                                )
                            )
                            .toByteArray()
                    )
            ComposedPack(result.toSortedMap(), baseIdentity)
        }
    }
}
