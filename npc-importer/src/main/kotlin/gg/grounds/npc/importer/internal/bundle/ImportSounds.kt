package gg.grounds.npc.importer.internal.bundle

import com.google.gson.*
import gg.grounds.npc.core.io.*
import gg.grounds.npc.importer.internal.*
import java.nio.file.*

internal object ImportSounds {
    fun apply(
        sources: ImportSources,
        models: MutableMap<String, JsonObject>,
        files: MutableMap<String, ByteArray>,
        budget: ImportBudget,
    ): JsonObject {
        val definitions = JsonObject()
        val sounds = sources.config.getAsJsonArray("sounds") ?: JsonArray()
        val context = ImportContext(sources.configSource)
        val declared = hashSetOf<String>()
        require(sounds.size() <= 256 * 128) { "Too many sound bindings" }
        for ((i, value) in sounds.withIndex()) context.at("$.sounds[$i]") {
            val sound = value.asJsonObject
            val modelId = sound["model"].asString
            val model = models[modelId] ?: error("Sound binding names unknown model $modelId")
            val clip = sound["clip"].asString
            require(model.getAsJsonObject("animations").has(clip)) {
                "Sound binding names unknown clip $clip"
            }
            require(declared.add("$modelId:$clip")) { "Duplicate clip sound binding" }
            val key = sound["key"].asString
            require(
                key.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) &&
                    key.substringAfter(':').split('/').none {
                        it.isEmpty() || it == "." || it == ".."
                    }
            ) {
                "Invalid sound ID"
            }
            val external = sound["external"]?.asBoolean == true
            val file = sound["file"]?.asString
            require((file != null) != external) {
                "Sound must provide a local OGG or explicitly declare external=true"
            }
            if (file != null) {
                val relative = Path.of(file)
                require(!relative.isAbsolute && ':' !in file && '\\' !in file) {
                    "Sound path must be relative"
                }
                val path = sources.root.resolve(relative).normalize()
                require(path.startsWith(sources.root)) { "Sound file escapes source directory" }
                var parent: Path? = path
                while (parent != null && parent.startsWith(sources.root)) {
                    require(!Files.isSymbolicLink(parent)) { "Symlink sound input is unsupported" }
                    parent = parent.parent
                }
                val name =
                    "assets/${key.substringBefore(':')}/sounds/${key.substringAfter(':')}.ogg"
                val maximum = budget.readLimit(name, 16 * 1024 * 1024, context.source)
                val bytes = Files.newInputStream(path).use { it.readNBytes(maximum + 1) }
                if (bytes.size > maximum) budget.exceeded(name, context.source)
                require(
                    bytes.size in 27..16 * 1024 * 1024 &&
                        bytes.copyOfRange(0, 4).contentEquals("OggS".toByteArray()) &&
                        bytes[4].toInt() == 0
                ) {
                    "Expected a bounded OGG file"
                }
                val previous = files[name]
                require(previous == null || previous.contentEquals(bytes)) {
                    "Conflicting sound file $name"
                }
                budget.charge(name, bytes.size, context.source)
                files[name] = bytes
                val definition =
                    ImportJson.objectOf("sounds" to listOf(ImportJson.objectOf("name" to key)))
                require(!definitions.has(key) || definitions[key] == definition) {
                    "Conflicting sound event $key"
                }
                definitions.add(key, definition)
            }
            model
                .getAsJsonObject("sounds")
                .add(
                    clip,
                    ImportJson.objectOf(
                        "key" to key,
                        "volume" to context.number(sound["volume"], "$.sounds[$i].volume", 1.0),
                        "pitch" to context.number(sound["pitch"], "$.sounds[$i].pitch", 1.0),
                        "radius" to context.number(sound["radius"], "$.sounds[$i].radius", 16.0),
                    ),
                )
            val decoded = NpcModelJson.decode(ImportJson.bytes(model))
            if (decoded is NpcDecodeResult.Failure)
                error(decoded.problems.joinToString { it.message })
        }
        for (namespace in definitions.keySet().map { it.substringBefore(':') }.toSet()) {
            val registry = JsonObject()
            for ((key, value) in definitions.entrySet()) if (key.substringBefore(':') == namespace)
                registry.add(key.substringAfter(':'), value)
            val bytes = ImportJson.bytes(registry)
            if (bytes.size > 4 * 1024 * 1024)
                fail(
                    context.source,
                    "assets/$namespace/sounds.json",
                    "Sound registry exceeds 4 MiB",
                    "NPC_IMPORT_LIMIT",
                )
            budget.charge("assets/$namespace/sounds.json", bytes.size, context.source)
            files["assets/$namespace/sounds.json"] = bytes
        }
        return definitions
    }
}
