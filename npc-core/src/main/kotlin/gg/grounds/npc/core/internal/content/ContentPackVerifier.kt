package gg.grounds.npc.core.internal.content

import com.google.gson.*
import java.nio.file.Path

internal object ContentPackVerifier {
    fun verify(path: Path, target: String, contract: JsonObject) =
        verify(ContentFiles.read(path, 256 * 1024 * 1024), target, contract)

    fun verify(resources: Map<String, ByteArray>, target: String, contract: JsonObject) {
        val files = ContentFiles.snapshot(resources, 256 * 1024 * 1024)
        val metadata =
            ContentJson.parse(
                files["pack.mcmeta"]
                    ?: contentFailure(
                        "NPC_CONTENT_PACK",
                        "pack.mcmeta",
                        "Pack metadata is missing",
                    ),
                "pack.mcmeta",
            )
        ContentJson.at("pack.mcmeta") {
            val version = if (target == "26.2") Version(88, 0) else Version(97, 1)
            val pack = metadata.getAsJsonObject("pack")
            val lower =
                pack["min_format"]?.let(::version)
                    ?: Version(ContentJson.integer(pack["pack_format"]), 0)
            val upper =
                pack["max_format"]?.let(::version)
                    ?: Version(ContentJson.integer(pack["pack_format"]), 0)
            require(lower <= version && version <= upper) {
                "Pack does not support content target $target"
            }
            val expected = contract.getAsJsonObject("clientFiles").keySet().toMutableSet()
            for ((id, _) in contract.getAsJsonObject("soundEvents").entrySet()) expected.add(
                "assets/${id.substringBefore(':')}/sounds.json"
            )
            checkShadows(metadata, files, expected, version)
        }
        for ((name, hash) in contract.getAsJsonObject("clientFiles").entrySet()) {
            val bytes =
                files[name]
                    ?: contentFailure("NPC_CONTENT_PACK", name, "Required client asset is missing")
            if (ContentJson.hash(bytes) != hash.asString)
                contentFailure("NPC_CONTENT_HASH", name, "Client asset differs from contract")
        }
        val registries = hashMapOf<String, JsonObject>()
        for ((id, definition) in contract.getAsJsonObject("soundEvents").entrySet()) {
            val name = "assets/${id.substringBefore(':')}/sounds.json"
            val registry =
                registries.getOrPut(name) {
                    ContentJson.parse(
                        files[name]
                            ?: contentFailure(
                                "NPC_CONTENT_PACK",
                                name,
                                "Sound registry is missing",
                            ),
                        name,
                    )
                }
            if (registry[id.substringAfter(':')] != definition)
                contentFailure(
                    "NPC_CONTENT_PACK",
                    "$name:$id",
                    "Contributed sound definition differs from contract",
                )
        }
    }

    private data class Version(val major: Int, val minor: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, Version::major, Version::minor)
    }

    private fun version(value: JsonElement): Version =
        if (value.isJsonArray) {
            require(value.asJsonArray.size() == 2)
            Version(
                ContentJson.integer(value.asJsonArray[0]),
                ContentJson.integer(value.asJsonArray[1]),
            )
        } else Version(ContentJson.integer(value), 0)

    private fun checkShadows(
        metadata: JsonObject,
        files: Map<String, ByteArray>,
        expected: Set<String>,
        target: Version,
    ) {
        metadata.getAsJsonObject("overlays")?.getAsJsonArray("entries")?.forEach { raw ->
            val entry = raw.asJsonObject
            val directory = ContentFiles.safeName(entry["directory"].asString)
            val range = entry["formats"]
            val active =
                when {
                    range == null ->
                        version(entry["min_format"]) <= target &&
                            target <= version(entry["max_format"])
                    range.isJsonObject ->
                        ContentJson.integer(range.asJsonObject["min_inclusive"]) <= target.major &&
                            target.major <= ContentJson.integer(range.asJsonObject["max_inclusive"])
                    range.isJsonArray ->
                        range.asJsonArray.any { ContentJson.integer(it) == target.major }
                    else -> ContentJson.integer(range) == target.major
                }
            if (active && expected.any { "$directory/$it" in files })
                contentFailure(
                    "NPC_CONTENT_PACK",
                    "pack.mcmeta.overlays",
                    "Active overlay shadows a required NPC resource",
                )
        }
        metadata.getAsJsonObject("filter")?.getAsJsonArray("block")?.forEach { raw ->
            val filter = raw.asJsonObject
            val namespace = Regex(filter["namespace"]?.asString ?: ".*")
            val path = Regex(filter["path"]?.asString ?: ".*")
            if (
                expected.any { name ->
                    val segments = name.removePrefix("assets/").split('/', limit = 2)
                    namespace.matches(segments[0]) && path.matches(segments[1])
                }
            ) {
                contentFailure(
                    "NPC_CONTENT_PACK",
                    "pack.mcmeta.filter",
                    "Pack filter matches a required NPC resource",
                )
            }
        }
    }
}
