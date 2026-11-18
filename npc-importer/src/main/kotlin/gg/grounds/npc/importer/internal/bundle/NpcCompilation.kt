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
    fun prepare(
        options: NpcImportOptions,
        clientBudget: ImportBudget = ImportBudget(),
        serverBudget: ImportBudget = ImportBudget(64 * 1024 * 1024),
    ): CompilationPlan {
        val sources = ImportSources.read(options)
        val context = ImportContext(options.input.toString())
        return context.at("$") {
            val models = linkedMapOf<String, JsonObject>()
            val files = linkedMapOf<String, ByteArray>()
            for (source in sources.sources) {
                val imported =
                    BlockbenchReader.read(source.path, source.id, sources.root, clientBudget)
                val resource =
                    "models/${source.id.substringBefore(':')}/${source.id.substringAfter(':')}.json"
                serverBudget.charge(resource, imported.modelJson.size, context.source)
                models[source.id] = context.parse(imported.modelJson)
                for ((name, bytes) in imported.resources) {
                    val existing = files[name]
                    require(existing == null || existing.contentEquals(bytes)) {
                        "Generated client resource collision: $name"
                    }
                    files[name] = bytes
                }
            }
            val sounds = ImportSounds.apply(sources, models, files, clientBudget)
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
                if (bytes.size > 2 * 1024 * 1024)
                    fail(
                        context.source,
                        resource,
                        "Compiled model exceeds 2 MiB",
                        "NPC_IMPORT_LIMIT",
                    )
                serverBudget.charge(resource, bytes.size, context.source)
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
            val contractBytes = ImportJson.bytes(contract)
            if (contractBytes.size > 4 * 1024 * 1024)
                fail(context.source, "bundle.json", "Contract exceeds 4 MiB", "NPC_IMPORT_LIMIT")
            serverBudget.charge("bundle.json", contractBytes.size, context.source)
            server["bundle.json"] = contractBytes
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
