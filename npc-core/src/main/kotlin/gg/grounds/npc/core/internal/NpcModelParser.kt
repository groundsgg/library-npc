package gg.grounds.npc.core.internal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.grounds.npc.core.animation.NpcBoneTrack
import gg.grounds.npc.core.animation.NpcClip
import gg.grounds.npc.core.animation.NpcInterpolation
import gg.grounds.npc.core.animation.NpcKeyframe
import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.io.NpcDecodeLimits
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcBone
import gg.grounds.npc.core.model.NpcBounds
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.model.NpcPart

/** Syntax decoding is separate from the shared model invariants. */
internal object NpcModelParser {
    private fun <T> at(path: String, read: () -> T): T =
        try {
            read()
        } catch (error: NpcValidationException) {
            throw error
        } catch (error: RuntimeException) {
            throw NpcValidationException(path, error.message ?: "Invalid field")
        }

    fun parse(json: String, limits: NpcDecodeLimits): NpcModel =
        at("$") {
            val root = JsonParser.parseString(json).asJsonObject
            at("$.schemaVersion") {
                require(root.get("schemaVersion")?.asBigDecimal?.intValueExact() == 1) {
                    "Unsupported NPC model schema"
                }
            }
            fun vector(obj: JsonObject, name: String, path: String): NpcVector3 =
                at("$path.$name") {
                    val values = requireNotNull(obj.getAsJsonArray(name)) { "Missing vector" }
                    require(values.size() == 3) { "Expected three finite components" }
                    NpcVector3(values[0].asDouble, values[1].asDouble, values[2].asDouble)
                }
            val bounds =
                at("$.bounds") {
                    val value = root.getAsJsonObject("bounds")
                    NpcBounds(
                        vector(value, "center", "$.bounds"),
                        vector(value, "size", "$.bounds"),
                    )
                }
            val bones =
                at("$.bones") {
                    root.getAsJsonArray("bones").mapIndexed { i, item ->
                        val path = "$.bones[$i]"
                        at(path) {
                            val bone = item.asJsonObject
                            NpcBone(
                                at("$path.id") { bone.get("id").asString },
                                at("$path.parent") {
                                    bone.get("parent")?.takeUnless { it.isJsonNull }?.asString
                                },
                                vector(bone, "origin", path),
                                vector(bone, "rotation", path),
                                at("$path.name") {
                                    bone.get("name")?.asString ?: bone.get("id").asString
                                },
                            )
                        }
                    }
                }
            val parts =
                at("$.parts") {
                    root.getAsJsonArray("parts").mapIndexed { i, item ->
                        val path = "$.parts[$i]"
                        at(path) {
                            val part = item.asJsonObject
                            NpcPart(
                                at("$path.bone") {
                                    part.get("bone")?.takeUnless { it.isJsonNull }?.asString
                                },
                                at("$path.model") { part.get("model").asString },
                                vector(part, "center", path),
                                vector(part, "pivot", path),
                                vector(part, "rotation", path),
                                if (part.has("scale")) vector(part, "scale", path)
                                else NpcVector3.ONE,
                            )
                        }
                    }
                }
            val clips =
                at("$.animations") {
                    root.getAsJsonObject("animations").entrySet().associate { (name, item) ->
                        val path = "$.animations.$name"
                        name to
                            at(path) {
                                val clip = item.asJsonObject
                                val mode =
                                    at("$path.loop") {
                                        when (clip.get("loop").asString) {
                                            "loop" -> NpcLoopMode.LOOP
                                            "once" -> NpcLoopMode.ONCE
                                            "hold" -> NpcLoopMode.HOLD
                                            else ->
                                                throw IllegalArgumentException(
                                                    "Expected loop, once or hold"
                                                )
                                        }
                                    }
                                val length = at("$path.length") { clip.get("length").asDouble }
                                val tracks =
                                    at("$path.tracks") {
                                        clip.getAsJsonObject("tracks").entrySet().associate {
                                            (bone, trackItem) ->
                                            val trackPath = "$path.tracks.$bone"
                                            bone to
                                                at(trackPath) {
                                                    val track = trackItem.asJsonObject
                                                    track.keySet().forEach { channel ->
                                                        at("$trackPath.$channel") {
                                                            require(
                                                                channel in
                                                                    setOf(
                                                                        "position",
                                                                        "rotation",
                                                                        "scale",
                                                                    )
                                                            ) {
                                                                "Unknown animation channel"
                                                            }
                                                        }
                                                    }
                                                    fun channel(name: String): List<NpcKeyframe> =
                                                        at("$trackPath.$name") {
                                                            track
                                                                .getAsJsonArray(name)
                                                                ?.mapIndexed { i, frameItem ->
                                                                    val keyPath =
                                                                        "$trackPath.$name[$i]"
                                                                    at(keyPath) {
                                                                        val frame =
                                                                            frameItem.asJsonObject
                                                                        val interpolation =
                                                                            at(
                                                                                "$keyPath.interpolation"
                                                                            ) {
                                                                                when (
                                                                                    frame
                                                                                        .get(
                                                                                            "interpolation"
                                                                                        )
                                                                                        .asString
                                                                                ) {
                                                                                    "linear" ->
                                                                                        NpcInterpolation
                                                                                            .LINEAR
                                                                                    "step" ->
                                                                                        NpcInterpolation
                                                                                            .STEP
                                                                                    "catmullrom" ->
                                                                                        NpcInterpolation
                                                                                            .CATMULL_ROM
                                                                                    else ->
                                                                                        throw IllegalArgumentException(
                                                                                            "Expected linear, step or catmullrom"
                                                                                        )
                                                                                }
                                                                            }
                                                                        NpcKeyframe(
                                                                            at("$keyPath.time") {
                                                                                frame
                                                                                    .get("time")
                                                                                    .asDouble
                                                                            },
                                                                            vector(
                                                                                frame,
                                                                                "value",
                                                                                keyPath,
                                                                            ),
                                                                            interpolation,
                                                                        )
                                                                    }
                                                                } ?: emptyList()
                                                        }
                                                    NpcBoneTrack(
                                                        channel("position"),
                                                        channel("rotation"),
                                                        channel("scale"),
                                                    )
                                                }
                                        }
                                    }
                                NpcClip(mode, length, tracks)
                            }
                    }
                }
            val sounds =
                at("$.sounds") {
                    root.getAsJsonObject("sounds").entrySet().associate { (clip, item) ->
                        val path = "$.sounds.$clip"
                        clip to
                            at(path) {
                                val sound = item.asJsonObject
                                NpcClipSound(
                                    at("$path.key") { sound.get("key").asString },
                                    at("$path.volume") { sound.get("volume").asDouble },
                                    at("$path.pitch") { sound.get("pitch").asDouble },
                                    at("$path.radius") { sound.get("radius").asDouble },
                                )
                            }
                    }
                }
            NpcModel(
                    at("$.asset") { root.get("asset").asString },
                    bounds,
                    bones,
                    parts,
                    clips,
                    sounds,
                )
                .also { NpcModelValidator.validate(it, limits) }
        }
}
