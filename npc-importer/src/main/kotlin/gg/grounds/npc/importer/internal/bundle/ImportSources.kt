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
                    ImportContext(path.toString())
                        .parse(
                            Files.newInputStream(path).use { it.readNBytes(4 * 1024 * 1024 + 1) }
                        )
                        .also { obj ->
                            context.at("$.config") {
                                require(obj["schemaVersion"].asInt == 1) {
                                    "Unsupported import configuration schema"
                                }
                                require(
                                    obj.keySet().all {
                                        it in setOf("schemaVersion", "models", "sounds")
                                    }
                                ) {
                                    "Unknown import configuration field"
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
                fail(context.source, "$.models", "Explicit model mapping names an absent source")
            return ImportSources(root, sources, config)
        }
    }
}
