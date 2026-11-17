package gg.grounds.npc.importer.internal.blockbench

import com.google.gson.*
import gg.grounds.npc.importer.internal.*

internal object BlockbenchAnimations {
    fun read(root: JsonObject, bones: Set<String>, legacy: Boolean, c: ImportContext): JsonObject {
        val result = JsonObject()
        val animations = root.getAsJsonArray("animations") ?: JsonArray()
        require(animations.size() <= 128) { "Too many animations" }
        for ((index, value) in animations.withIndex()) c.at("$.animations[$index]") {
            val animation = value.asJsonObject
            val name = animation["name"].asString
            require(!result.has(name)) { "Duplicate animation name: $name" }
            for (field in listOf("anim_time_update", "blend_weight", "start_delay", "loop_delay")) {
                val v = animation[field]?.asString
                require(
                    v == null || v.isBlank() || v == "0" || (field == "blend_weight" && v == "1")
                ) {
                    "Unsupported animation expression: $field"
                }
            }
            val loop = animation["loop"]?.asString ?: "once"
            require(loop in listOf("once", "loop", "hold")) { "Unsupported loop mode $loop" }
            val tracks = JsonObject()
            for ((bone, raw) in animation.getAsJsonObject("animators")?.entrySet() ?: emptySet()) {
                val animator = raw.asJsonObject
                val keys = animator.getAsJsonArray("keyframes") ?: JsonArray()
                if (keys.isEmpty) continue
                require(
                    bone in bones &&
                        (animator["type"]?.asString ?: "bone") == "bone" &&
                        animator["rotation_global"]?.asBoolean != true &&
                        animator["quaternion_interpolation"]?.asBoolean != true
                ) {
                    "Unsupported animator $bone"
                }
                require(keys.size() <= 3 * 4096) { "Too many keyframes" }
                val channels =
                    linkedMapOf(
                        "position" to mutableListOf<JsonObject>(),
                        "rotation" to mutableListOf<JsonObject>(),
                        "scale" to mutableListOf<JsonObject>(),
                    )
                for ((keyIndex, rawKey) in keys.withIndex()) {
                    val path = "$.animations[$index].animators.$bone.keyframes[$keyIndex]"
                    c.at(path) {
                        val key = rawKey.asJsonObject
                        val channel = key["channel"].asString
                        require(channel in channels) { "Unsupported keyframe channel $channel" }
                        val interpolation = key["interpolation"]?.asString ?: "linear"
                        require(interpolation in listOf("linear", "step", "catmullrom")) {
                            "Unsupported interpolation $interpolation"
                        }
                        val points = key.getAsJsonArray("data_points")
                        require(points.size() == 1) {
                            "Only numeric single-point keyframes are supported"
                        }
                        val point = points[0].asJsonObject
                        val vector =
                            listOf("x", "y", "z").mapIndexed { axis, coordinate ->
                                var number =
                                    c.number(
                                        point[coordinate],
                                        "$path.data_points[0].$coordinate",
                                        if (channel == "scale") 1.0 else 0.0,
                                    )
                                if (
                                    legacy &&
                                        ((axis == 0 && channel != "scale") ||
                                            (axis == 1 && channel == "rotation"))
                                )
                                    number = -number
                                if (channel == "position") number /= 16
                                number
                            }
                        channels
                            .getValue(channel)
                            .add(
                                ImportJson.objectOf(
                                    "time" to c.number(key["time"], "$path.time"),
                                    "value" to vector,
                                    "interpolation" to interpolation,
                                )
                            )
                    }
                }
                tracks.add(
                    bone,
                    JsonObject().apply {
                        for ((channel, values) in channels) add(
                            channel,
                            ImportJson.gson.toJsonTree(values.sortedBy { it["time"].asDouble }),
                        )
                    },
                )
            }
            result.add(
                name,
                ImportJson.objectOf(
                    "loop" to loop,
                    "length" to c.number(animation["length"], "$.animations[$index].length"),
                    "tracks" to tracks,
                ),
            )
        }
        return result
    }
}
