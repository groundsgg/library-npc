package gg.grounds.npc.importer.internal.bundle

import com.google.gson.*
import gg.grounds.npc.importer.config.NpcImportOptions
import gg.grounds.npc.importer.internal.*
import java.nio.file.*
import java.util.Locale

internal data class ImportSource(val path: Path, val relative: String, val id: String)

internal class ImportSources(
    val root: Path,
    val sources: List<ImportSource>,
    val config: JsonObject,
    val configSource: String,
) {
    companion object {
        fun read(options: NpcImportOptions): ImportSources {
            val context = ImportContext(options.input.toString())
            if (Files.isSymbolicLink(options.input))
                fail(context.source, "$", "Symlink input is unsupported")
            val root =
                if (Files.isDirectory(options.input, LinkOption.NOFOLLOW_LINKS)) options.input
                else options.input.parent
            val config =
                options.config?.let { path ->
                    val configContext = ImportContext(path.toString())
                    configContext.at("$") {
                        require(!Files.isSymbolicLink(path)) {
                            "Symlink configuration is unsupported"
                        }
                    }
                    val bytes =
                        Files.newInputStream(path).use { it.readNBytes(4 * 1024 * 1024 + 1) }
                    configContext.at("$") {
                        require(bytes.size <= 4 * 1024 * 1024) { "Configuration exceeds 4 MiB" }
                    }
                    configContext.parse(bytes).also { obj ->
                        configContext.at("$.schemaVersion") {
                            val schema = obj["schemaVersion"]
                            require(
                                schema != null &&
                                    schema.isJsonPrimitive &&
                                    schema.asJsonPrimitive.isNumber &&
                                    schema.asBigDecimal.intValueExact() == 1
                            ) {
                                "Expected integer import configuration schema 1"
                            }
                        }
                        configContext.at("$") {
                            require(
                                obj.keySet().all {
                                    it in setOf("schemaVersion", "models", "sounds")
                                }
                            ) {
                                "Unknown import configuration field"
                            }
                        }
                        obj["models"]?.let { raw ->
                            configContext.at("$.models") {
                                require(raw.isJsonObject) { "Expected a model identity mapping" }
                            }
                            for ((name, id) in raw.asJsonObject.entrySet()) configContext.at(
                                "$.models.$name"
                            ) {
                                require(
                                    id.isJsonPrimitive &&
                                        id.asJsonPrimitive.isString &&
                                        id.asString.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) &&
                                        id.asString.length <= 256 &&
                                        id.asString.substringAfter(':').split('/').none {
                                            it.isEmpty() || it == "." || it == ".."
                                        }
                                ) {
                                    "Expected a valid namespaced model identity"
                                }
                            }
                        }
                        obj["sounds"]?.let { raw ->
                            configContext.at("$.sounds") {
                                require(raw.isJsonArray && raw.asJsonArray.size() <= 8192) {
                                    "Expected at most 8192 sound bindings"
                                }
                            }
                            for ((i, binding) in raw.asJsonArray.withIndex()) configContext.at(
                                "$.sounds[$i]"
                            ) {
                                require(binding.isJsonObject) { "Expected a sound binding object" }
                            }
                        }
                    }
                } ?: JsonObject()
            val paths =
                if (Files.isDirectory(options.input, LinkOption.NOFOLLOW_LINKS)) {
                    Files.walk(options.input).use { stream ->
                        stream
                            .filter { path ->
                                if (Files.isSymbolicLink(path))
                                    fail(
                                        context.source,
                                        "$",
                                        "Symlink source path is unsupported: ${root.relativize(path)}",
                                    )
                                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                                    path.fileName
                                        .toString()
                                        .lowercase(Locale.ROOT)
                                        .endsWith(".bbmodel")
                            }
                            .limit(257)
                            .sorted()
                            .toList()
                    }
                } else {
                    if (!Files.exists(options.input))
                        throw java.nio.file.NoSuchFileException(options.input.toString())
                    listOf(options.input)
                }
            if (paths.isEmpty() || paths.size > 256)
                fail(context.source, "$", "Expected 1..256 Blockbench models")
            val ids = hashSetOf<String>()
            val relatives = hashSetOf<String>()
            val overrides = config.getAsJsonObject("models") ?: JsonObject()
            val sources = paths.map { path ->
                val relative = root.relativize(path).joinToString("/")
                if (!relative.lowercase(Locale.ROOT).endsWith(".bbmodel"))
                    fail(context.source, "$", "Expected a .bbmodel source")
                relatives.add(relative)
                val derived = relative.dropLast(8).lowercase(Locale.ROOT)
                val id = overrides[relative]?.asString ?: "${options.namespace}:npc/$derived"
                if (!ids.add(id))
                    fail(
                        context.source,
                        "$.models",
                        "Duplicate normalized model ID: $id",
                        "NPC_IMPORT_DUPLICATE",
                    )
                ImportSource(path, relative, id)
            }
            if (!relatives.containsAll(overrides.keySet()))
                fail(
                    options.config?.toString() ?: context.source,
                    "$.models",
                    "Explicit model mapping names an absent source",
                )
            return ImportSources(
                root,
                sources,
                config,
                options.config?.toString() ?: options.input.toString(),
            )
        }
    }
}
