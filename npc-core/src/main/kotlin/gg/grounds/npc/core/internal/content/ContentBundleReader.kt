package gg.grounds.npc.core.internal.content

import gg.grounds.npc.core.io.NpcDecodeResult
import gg.grounds.npc.core.io.NpcModelJson
import gg.grounds.npc.core.io.content.NpcContent
import java.nio.file.Path

internal object ContentBundleReader {
    fun load(path: Path): NpcContent {
        val files = ContentFiles.read(path, 64 * 1024 * 1024)
        val raw =
            files["bundle.json"]
                ?: contentFailure(
                    "NPC_CONTENT_CONTRACT",
                    "bundle.json",
                    "Content contract is missing",
                )
        if (raw.size > 4 * 1024 * 1024)
            contentFailure("NPC_CONTENT_LIMIT", "bundle.json", "Contract is too large")
        val contract = ContentJson.parse(raw, "bundle.json")
        return ContentJson.at("bundle.json") {
            require(ContentJson.integer(contract["bundleSchema"]) == 1) {
                "Unsupported bundle schema"
            }
            require(contract["compilerVersion"].asString.isNotBlank()) {
                "Compiler version is missing"
            }
            val target = contract["target"].asString
            require(target == "26.2" || target == "26.3") { "Unsupported target $target" }
            val identity = ContentJson.digest(contract["contentId"])
            val payload = contract.deepCopy().apply { remove("contentId") }
            if (ContentJson.hash(ContentJson.canonical(payload).toByteArray()) != identity)
                contentFailure(
                    "NPC_CONTENT_ID",
                    "bundle.json.contentId",
                    "Content identity does not match contract",
                )
            val entries = contract.getAsJsonArray("models")
            require(entries.size() <= 256) { "Too many models" }
            val resources = hashSetOf("bundle.json")
            val models = linkedMapOf<String, gg.grounds.npc.core.model.NpcModel>()
            for ((i, entry) in entries.withIndex()) {
                val item = entry.asJsonObject
                val id = item["id"].asString
                val name = ContentFiles.safeName(item["resource"].asString)
                if (id in models || !resources.add(name))
                    contentFailure(
                        "NPC_CONTENT_DUPLICATE",
                        "bundle.json.models[$i]",
                        "Duplicate model ID or resource",
                    )
                val bytes =
                    files[name]
                        ?: contentFailure("NPC_CONTENT_CONTRACT", name, "Model resource is missing")
                if (bytes.size > 2 * 1024 * 1024)
                    contentFailure("NPC_CONTENT_LIMIT", name, "Model exceeds 2 MiB")
                if (ContentJson.hash(bytes) != ContentJson.digest(item["sha256"]))
                    contentFailure("NPC_CONTENT_HASH", name, "Model bytes differ from contract")
                ContentJson.parse(bytes, name)
                val decoded = NpcModelJson.decode(bytes)
                if (decoded !is NpcDecodeResult.Success)
                    contentFailure(
                        "NPC_CONTENT_MODEL",
                        name,
                        (decoded as NpcDecodeResult.Failure).problems.joinToString {
                            "${it.path}: ${it.message}"
                        },
                    )
                if (decoded.model.id != id)
                    contentFailure(
                        "NPC_CONTENT_MODEL_ID",
                        name,
                        "Model identity differs from index",
                    )
                models[id] = decoded.model
            }
            require(files.keys == resources) { "Unexpected content payload outside contract" }
            for ((name, hash) in contract.getAsJsonObject("clientFiles").entrySet()) {
                ContentFiles.safeName(name)
                require(name.startsWith("assets/")) { "Client file must be an asset" }
                ContentJson.digest(hash)
            }
            for ((id, sound) in contract.getAsJsonObject("soundEvents").entrySet()) {
                require(id.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "Invalid sound ID" }
                require(
                    sound.isJsonObject && sound.asJsonObject.get("sounds")?.isJsonArray == true
                ) {
                    "Invalid sound event definition"
                }
            }
            NpcContent(identity, target, models, contract)
        }
    }
}
