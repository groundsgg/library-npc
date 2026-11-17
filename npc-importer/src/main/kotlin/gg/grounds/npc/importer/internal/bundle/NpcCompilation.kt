package gg.grounds.npc.importer.internal.bundle

import com.google.gson.*
import gg.grounds.npc.core.io.content.*
import gg.grounds.npc.importer.config.NpcImportOptions
import gg.grounds.npc.importer.error.*
import gg.grounds.npc.importer.internal.*
import gg.grounds.npc.importer.internal.blockbench.BlockbenchReader
import gg.grounds.npc.importer.internal.pack.PackComposer

internal data class CompilationPlan(
    val server: Map<String, ByteArray>,
    val pack: Map<String, ByteArray>,
    val fragment: Map<String, ByteArray>,
    val contentId: String,
    val target: String,
    val baseId: String?,
)

internal object NpcCompilation {
    fun prepare(options: NpcImportOptions): CompilationPlan {
        val sources = ImportSources.read(options)
        val context = ImportContext(options.input.toString())
        return context.at("$") {
            val models = linkedMapOf<String, JsonObject>()
            val files = linkedMapOf<String, ByteArray>()
            for (source in sources.sources) {
                val imported = BlockbenchReader.read(source.path, source.id, sources.root)
                models[source.id] = context.parse(imported.modelJson)
                for ((name, bytes) in imported.resources) {
                    val existing = files[name]
                    require(existing == null || existing.contentEquals(bytes)) {
                        "Generated client resource collision: $name"
                    }
                    files[name] = bytes
                }
            }
            val sounds = ImportSounds.apply(sources, models, files)
            val contract =
                ImportJson.objectOf(
                    "bundleSchema" to 1,
                    "compilerVersion" to ImporterVersion.value,
                    "target" to options.target.version,
                )
            val entries = JsonArray()
            val server = linkedMapOf<String, ByteArray>()
            for ((id, json) in models.toSortedMap()) {
                val resource = "models/${id.substringBefore(':')}/${id.substringAfter(':')}.json"
                val bytes = ImportJson.bytes(json)
                server[resource] = bytes
                entries.add(
                    ImportJson.objectOf(
                        "id" to id,
                        "resource" to resource,
                        "sha256" to ImportJson.hash(bytes),
                    )
                )
            }
            contract.add("models", entries)
            contract.add(
                "clientFiles",
                ImportJson.gson.toJsonTree(
                    files
                        .filterKeys { !it.endsWith("/sounds.json") }
                        .mapValues { ImportJson.hash(it.value) }
                ),
            )
            contract.add("soundEvents", sounds)
            val identity = ImportJson.hash(ImportJson.canonical(contract).toByteArray())
            contract.addProperty("contentId", identity)
            server["bundle.json"] = ImportJson.bytes(contract)
            val pack = PackComposer.compose(options, files)
            try {
                val content = NpcContentLoader.load(server)
                content.verifyResourcePack(pack.files)
            } catch (e: NpcContentException) {
                throw NpcImportException(
                    e.problems.map {
                        NpcImportProblem(it.code, "compiled content", it.path, it.message)
                    }
                )
            }
            CompilationPlan(
                server.toSortedMap(),
                pack.files,
                files.toSortedMap(),
                identity,
                options.target.version,
                pack.baseId,
            )
        }
    }
}
