package gg.grounds.npc.core.internal

import com.google.gson.JsonElement
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
            Decoder(JsonParser.parseString(json).asJsonObject, limits).decode()
        }

    private class Decoder(private val root: JsonObject, private val limits: NpcDecodeLimits) {
        fun decode(): NpcModel {
            at("$.schemaVersion") {
                require(root.get("schemaVersion")?.asBigDecimal?.intValueExact() == 1) {
                    "Unsupported NPC model schema"
                }
            }
            return NpcModel(
                at("$.asset") { root.get("asset").asString },
                bounds(),
                bones(),
                parts(),
                clips(),
                sounds(),
                limits,
            )
        }

        private fun bounds(): NpcBounds =
            at("$.bounds") {
                val value = root.getAsJsonObject("bounds")
                val center = vector(value, "center", "$.bounds")
                val size = vector(value, "size", "$.bounds")
                at("$.bounds.size") { NpcBounds(center, size) }
            }

        private fun bones(): List<NpcBone> =
            at("$.bones") {
                val values = root.getAsJsonArray("bones")
                require(values.size() <= limits.maxBones) {
                    "Too many bones; maximum ${limits.maxBones}"
                }
                values.mapIndexed { i, item ->
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

        private fun parts(): List<NpcPart> =
            at("$.parts") {
                val values = root.getAsJsonArray("parts")
                require(values.size() in 1..limits.maxParts) {
                    "Expected 1..${limits.maxParts} parts"
                }
                values.mapIndexed { i, item ->
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
                            if (part.has("scale")) vector(part, "scale", path) else NpcVector3.ONE,
                        )
                    }
                }
            }

        private fun clips(): Map<String, NpcClip> =
            at("$.animations") {
                val values = root.getAsJsonObject("animations")
                require(values.size() <= limits.maxClips) {
                    "Too many clips; maximum ${limits.maxClips}"
                }
                values.entrySet().associate { (name, item) ->
                    name to clip(item, NpcJsonPath.member("$.animations", name))
                }
            }

        private fun clip(item: JsonElement, path: String): NpcClip =
            at(path) {
                val clip = item.asJsonObject
                val mode =
                    at("$path.loop") {
                        when (clip.get("loop").asString) {
                            "loop" -> NpcLoopMode.LOOP
                            "once" -> NpcLoopMode.ONCE
                            "hold" -> NpcLoopMode.HOLD
                            else -> throw IllegalArgumentException("Expected loop, once or hold")
                        }
                    }
                val length =
                    at("$path.length") {
                        clip.get("length").asDouble.also {
                            require(it.isFinite() && it > 0 && it <= limits.maxClipSeconds) {
                                "Clip duration must be finite and in (0, ${limits.maxClipSeconds}] seconds"
                            }
                        }
                    }
                val tracks =
                    at("$path.tracks") {
                        clip.getAsJsonObject("tracks").entrySet().associate { (bone, value) ->
                            bone to track(value, NpcJsonPath.member("$path.tracks", bone))
                        }
                    }
                NpcClip(mode, length, tracks)
            }

        private fun track(item: JsonElement, path: String): NpcBoneTrack =
            at(path) {
                val track = item.asJsonObject
                track.keySet().forEach { name ->
                    at(NpcJsonPath.member(path, name)) {
                        require(name in setOf("position", "rotation", "scale")) {
                            "Unknown animation channel"
                        }
                    }
                }
                NpcBoneTrack(
                    channel(track, "position", path),
                    channel(track, "rotation", path),
                    channel(track, "scale", path),
                )
            }

        private fun channel(track: JsonObject, name: String, path: String): List<NpcKeyframe> =
            at("$path.$name") {
                val keys = track.getAsJsonArray(name) ?: return@at emptyList()
                require(keys.size() <= limits.maxKeysPerTrack) {
                    "Too many keys; maximum ${limits.maxKeysPerTrack}"
                }
                keys.mapIndexed { i, value -> keyframe(value, "$path.$name[$i]") }
            }

        private fun keyframe(item: JsonElement, path: String): NpcKeyframe =
            at(path) {
                val frame = item.asJsonObject
                val interpolation =
                    at("$path.interpolation") {
                        when (frame.get("interpolation").asString) {
                            "linear" -> NpcInterpolation.LINEAR
                            "step" -> NpcInterpolation.STEP
                            "catmullrom" -> NpcInterpolation.CATMULL_ROM
                            else ->
                                throw IllegalArgumentException(
                                    "Expected linear, step or catmullrom"
                                )
                        }
                    }
                NpcKeyframe(
                    at("$path.time") { frame.get("time").asDouble },
                    vector(frame, "value", path),
                    interpolation,
                )
            }

        private fun sounds(): Map<String, NpcClipSound> =
            at("$.sounds") {
                root.getAsJsonObject("sounds").entrySet().associate { (clip, item) ->
                    val path = NpcJsonPath.member("$.sounds", clip)
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
    }

    private fun vector(obj: JsonObject, name: String, path: String): NpcVector3 =
        at("$path.$name") {
            val values = requireNotNull(obj.getAsJsonArray(name)) { "Missing vector" }
            require(values.size() == 3) { "Expected three finite components" }
            fun component(index: Int): Double =
                at("$path.$name[$index]") {
                    values[index].asDouble.also {
                        require(it.isFinite()) { "Expected a finite component" }
                    }
                }
            NpcVector3(component(0), component(1), component(2))
        }
}
