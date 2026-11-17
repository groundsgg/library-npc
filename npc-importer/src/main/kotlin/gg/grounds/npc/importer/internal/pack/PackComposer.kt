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
        return context.at("pack.mcmeta") {
            val metadata =
                if (options.basePack == null) JsonObject()
                else context.parse(base["pack.mcmeta"] ?: error("Base pack metadata is missing"))
            val pack =
                metadata.getAsJsonObject("pack") ?: JsonObject().also { metadata.add("pack", it) }
            val target = options.target
            fun version(raw: JsonElement): Pair<Int, Int> =
                if (raw.isJsonArray) {
                    require(raw.asJsonArray.size() == 2)
                    raw.asJsonArray[0].asBigDecimal.intValueExact() to
                        raw.asJsonArray[1].asBigDecimal.intValueExact()
                } else raw.asBigDecimal.intValueExact() to 0
            fun less(a: Pair<Int, Int>, b: Pair<Int, Int>) =
                a.first < b.first || (a.first == b.first && a.second < b.second)
            if (options.basePack != null) {
                val low = version(pack["min_format"] ?: pack["pack_format"])
                val high = version(pack["max_format"] ?: pack["pack_format"])
                val selected = target.major to target.minor
                require(!less(selected, low) && !less(high, selected)) {
                    "Base pack does not support Minecraft ${target.version}"
                }
            }
            pack.addProperty("pack_format", target.major)
            pack.add("min_format", ImportJson.gson.toJsonTree(listOf(target.major, target.minor)))
            pack.add("max_format", ImportJson.gson.toJsonTree(listOf(target.major, target.minor)))
            if (options.description != null) pack.addProperty("description", options.description)
            else if (!pack.has("description")) pack.addProperty("description", "NPC models")
            val result = base.toMutableMap()
            for ((name, bytes) in generated) {
                val old = result[name]
                if (old == null || old.contentEquals(bytes)) result[name] = bytes
                else if (name.matches(Regex("assets/[^/]+/sounds\\.json"))) {
                    val registry = context.parse(old)
                    for ((key, definition) in context.parse(bytes).entrySet()) {
                        require(!registry.has(key) || registry[key] == definition) {
                            "Conflicting sound event $name:$key"
                        }
                        registry.add(key, definition)
                    }
                    result[name] = ImportJson.bytes(registry)
                } else
                    fail(
                        source,
                        name,
                        "Base and generated assets differ at the same path",
                        "NPC_IMPORT_COLLISION",
                    )
            }
            result["pack.mcmeta"] = ImportJson.bytes(metadata)
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
