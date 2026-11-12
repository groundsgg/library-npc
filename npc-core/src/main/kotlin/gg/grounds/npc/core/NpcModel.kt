// Extracted from Grounds resourcepacks v1.3.0, commit c92fcaffe95e02bf4a6badba700a4c40b5724016.
// See LICENSE and NOTICE for source provenance.
package gg.grounds.npc.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class NpcKeyframe(val time: Double, val value: NpcVector3, val interpolation: String)

data class NpcBone
@JvmOverloads
constructor(
    val id: String,
    val parent: String?,
    val origin: NpcVector3,
    val rotation: NpcVector3,
    val name: String = id,
)

data class NpcPart(
    val bone: String?,
    val model: String,
    val center: NpcVector3,
    val pivot: NpcVector3,
    val rotation: NpcVector3,
    val scale: NpcVector3 = NpcVector3(1.0, 1.0, 1.0),
)

class NpcClip(
    val loop: String,
    val length: Double,
    tracks: Map<String, Map<String, List<NpcKeyframe>>>,
) {
    val tracks: Map<String, Map<String, List<NpcKeyframe>>> =
        immutableMap(
            tracks.mapValues { (_, channels) ->
                immutableMap(channels.mapValues { (_, keys) -> immutableList(keys) })
            }
        )
}

data class NpcClipSound(val key: String, val volume: Double, val pitch: Double, val radius: Double)

/** Compiled, numeric asset data. Parsing never evaluates authored expressions or scripts. */
class NpcModel(
    val id: String,
    val bounds: NpcBounds,
    bones: List<NpcBone>,
    parts: List<NpcPart>,
    animations: Map<String, NpcClip>,
    sounds: Map<String, NpcClipSound>,
) {
    val bones: List<NpcBone> = immutableList(bones)
    val parts: List<NpcPart> = immutableList(parts)
    val animations: Map<String, NpcClip> = immutableMap(animations)
    val sounds: Map<String, NpcClipSound> = immutableMap(sounds)

    init {
        resourceId(id)
        require(this.bones.size <= 256 && this.parts.size in 1..1024 && this.animations.size <= 128)
        val known = HashSet<String>()
        for (bone in this.bones) {
            localId(bone.id)
            require(bone.parent == null || bone.parent in known) { "Missing or cyclic NPC parent" }
            require(known.add(bone.id)) { "Duplicate bone" }
        }
        for (part in this.parts) {
            require(part.bone == null || part.bone in known) { "Missing part bone" }
            resourceId(part.model)
        }
        for ((name, clip) in this.animations) {
            localId(name)
            require(clip.length.isFinite() && clip.length > 0 && clip.length <= 300)
            require(clip.loop in setOf("loop", "once", "hold"))
            for ((bone, channels) in clip.tracks) {
                require(bone in known) { "Missing track bone" }
                for ((channel, keys) in channels) {
                    require(channel in setOf("position", "rotation", "scale"))
                    require(
                        keys.size <= 4096 && keys.zipWithNext().all { (a, b) -> a.time <= b.time }
                    )
                    require(
                        keys.all {
                            it.time.isFinite() &&
                                it.time >= 0 &&
                                it.time <= clip.length + 1e-4 &&
                                it.interpolation in setOf("linear", "step", "catmullrom")
                        }
                    )
                }
            }
        }
        for ((clip, sound) in this.sounds) {
            require(clip in this.animations)
            resourceId(sound.key)
            require(sound.volume.isFinite() && sound.volume > 0 && sound.volume <= 4)
            require(sound.pitch.isFinite() && sound.pitch > 0)
            require(sound.radius.isFinite() && sound.radius > 0 && sound.radius <= 64)
        }
    }

    val headBone: NpcBone? = bones.singleOrNull { it.name.equals("head", ignoreCase = true) }
    private val boneIndices = bones.mapIndexed { index, bone -> bone.id to index }.toMap()
    private val origins = bones.associate { it.id to it.origin }
    private val parents = IntArray(bones.size) { bones[it].parent?.let(boneIndices::get) ?: -1 }
    private val headIndex = headBone?.id?.let(boneIndices::get) ?: -1
    private val bindBones = bones.map { bone ->
        NpcMatrix.translation(bone.origin - (bone.parent?.let(origins::get) ?: ZERO)) *
            NpcMatrix.rotation(bone.rotation)
    }
    private val partBones = IntArray(parts.size) { parts[it].bone?.let(boneIndices::get) ?: -1 }
    private val bindParts = parts.map { part ->
        NpcMatrix.translation(part.pivot - (part.bone?.let(origins::get) ?: ZERO)) *
            NpcMatrix.rotation(part.rotation) *
            NpcMatrix.translation(part.center - part.pivot) *
            NpcMatrix.scale(part.scale)
    }

    internal fun transformedParts(postTransform: NpcMatrix): List<NpcMatrix> = bindParts.map {
        it * postTransform
    }

    @JvmOverloads
    internal fun pose(
        animation: String?,
        seconds: Double,
        headLook: NpcVector3? = null,
    ): List<NpcMatrix> = samplePose(animation, seconds, headLook, bindParts)

    internal fun samplePose(
        animation: String?,
        seconds: Double,
        headLook: NpcVector3?,
        localParts: List<NpcMatrix>,
    ): List<NpcMatrix> {
        val clip = animation?.let(animations::get)
        val time = if (clip?.loop == "loop") seconds % clip.length else seconds
        val world = arrayOfNulls<NpcMatrix>(bones.size)
        val look = headLook?.takeUnless { it == ZERO }?.let(NpcMatrix::rotation)
        for ((index, bone) in bones.withIndex()) {
            val channels = clip?.tracks?.get(bone.id).orEmpty()
            if (channels.values.all { it.isEmpty() }) {
                val local =
                    if (index == headIndex && look != null) bindBones[index] * look
                    else bindBones[index]
                world[index] = (world.getOrNull(parents[index]) ?: NpcMatrix.IDENTITY) * local
                continue
            }
            val position =
                sampleNpcTrack(channels["position"].orEmpty(), time, ZERO, clip?.loop == "loop")
            val rotation =
                bone.rotation +
                    sampleNpcTrack(channels["rotation"].orEmpty(), time, ZERO, clip?.loop == "loop")
            val scale = sampleNpcTrack(channels["scale"].orEmpty(), time, ONE, clip?.loop == "loop")
            val local =
                NpcMatrix.translation(
                    bone.origin - (bone.parent?.let(origins::get) ?: ZERO) + position
                ) *
                    NpcMatrix.rotation(rotation) *
                    (if (index == headIndex && look != null) look else NpcMatrix.IDENTITY) *
                    NpcMatrix.scale(scale)
            world[index] = (world.getOrNull(parents[index]) ?: NpcMatrix.IDENTITY) * local
        }
        return localParts.mapIndexed { index, local ->
            // Sampled matrices are immutable; callers receive defensive array snapshots through
            // values.
            (world.getOrNull(partBones[index]) ?: NpcMatrix.IDENTITY) * local
        }
    }

    companion object {
        internal fun parse(json: String, limits: NpcDecodeLimits): NpcModel {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("schemaVersion")?.asBigDecimal?.intValueExact() == 1) {
                "Unsupported NPC model schema"
            }
            fun vector(obj: JsonObject, name: String): NpcVector3 {
                val values = requireNotNull(obj.getAsJsonArray(name)) { "Missing $name" }
                require(values.size() == 3)
                return NpcVector3(values[0].asDouble, values[1].asDouble, values[2].asDouble)
            }
            val bounds = root.getAsJsonObject("bounds")
            val bones =
                root.getAsJsonArray("bones").map { item ->
                    val bone = item.asJsonObject
                    NpcBone(
                        bone.get("id").asString,
                        bone.get("parent")?.takeUnless { it.isJsonNull }?.asString,
                        vector(bone, "origin"),
                        vector(bone, "rotation"),
                        bone.get("name")?.asString ?: bone.get("id").asString,
                    )
                }
            require(bones.size <= limits.maxBones)
            val known = mutableSetOf<String>()
            bones.forEach { bone ->
                require(bone.parent == null || bone.parent in known) {
                    "Missing or cyclic NPC parent"
                }
                require(known.add(bone.id)) { "Duplicate bone" }
            }
            val parts =
                root.getAsJsonArray("parts").map { item ->
                    val part = item.asJsonObject
                    val bone = part.get("bone")?.takeUnless { it.isJsonNull }?.asString
                    require(bone == null || bone in known)
                    val model = resourceId(part.get("model").asString)
                    NpcPart(
                        bone,
                        model,
                        vector(part, "center"),
                        vector(part, "pivot"),
                        vector(part, "rotation"),
                        if (part.has("scale")) vector(part, "scale") else ONE,
                    )
                }
            require(parts.size in 1..limits.maxParts)
            val clips =
                root.getAsJsonObject("animations").entrySet().associate { (name, item) ->
                    localId(name)
                    val clip = item.asJsonObject
                    val length = clip.get("length").asDouble
                    require(length.isFinite() && length > 0 && length <= limits.maxClipSeconds)
                    val loop = clip.get("loop").asString
                    require(loop in setOf("loop", "once", "hold"))
                    val tracks =
                        clip.getAsJsonObject("tracks").entrySet().associate { (bone, trackItem) ->
                            require(bone in known)
                            bone to
                                trackItem.asJsonObject.entrySet().associate { (channel, frames) ->
                                    require(channel in setOf("position", "rotation", "scale"))
                                    val keys =
                                        frames.asJsonArray.map { frameItem ->
                                            val frame = frameItem.asJsonObject
                                            val time = frame.get("time").asDouble
                                            val interpolation = frame.get("interpolation").asString
                                            require(
                                                time.isFinite() &&
                                                    time >= 0 &&
                                                    time <= length + 1e-4
                                            )
                                            require(
                                                interpolation in
                                                    setOf("linear", "catmullrom", "step")
                                            )
                                            NpcKeyframe(time, vector(frame, "value"), interpolation)
                                        }
                                    require(
                                        keys.size <= limits.maxKeysPerTrack &&
                                            keys.zipWithNext().all { (a, b) -> a.time <= b.time }
                                    )
                                    channel to keys
                                }
                        }
                    name to NpcClip(loop, length, tracks)
                }
            require(clips.size <= limits.maxClips)
            val sounds =
                root.getAsJsonObject("sounds").entrySet().associate { (clip, item) ->
                    require(clip in clips)
                    val value = item.asJsonObject
                    val key = resourceId(value.get("key").asString)
                    val volume = value.get("volume").asDouble
                    val pitch = value.get("pitch").asDouble
                    val radius = value.get("radius").asDouble
                    require(
                        volume.isFinite() &&
                            volume > 0 &&
                            volume <= 4 &&
                            pitch.isFinite() &&
                            pitch > 0 &&
                            radius.isFinite() &&
                            radius > 0 &&
                            radius <= 64
                    )
                    clip to NpcClipSound(key, volume, pitch, radius)
                }
            return NpcModel(
                resourceId(root.get("asset").asString),
                NpcBounds(vector(bounds, "center"), vector(bounds, "size")),
                bones,
                parts,
                clips,
                sounds,
            )
        }
    }
}

/**
 * Matches Blockbench Keyframe.getCatmullromLerp / THREE.SplineCurve, including endpoint duplication
 * for one-shots and wrapped tangent keys for looping clips.
 */
fun sampleNpcTrack(
    keys: List<NpcKeyframe>,
    time: Double,
    fallback: NpcVector3,
    loop: Boolean = false,
): NpcVector3 {
    if (keys.isEmpty()) return fallback
    if (time < keys.first().time) return keys.first().value
    val before = keys.indexOfLast { it.time <= time }
    if (before == keys.lastIndex) return keys.last().value
    val a = keys[before]
    val b = keys[before + 1]
    if (a.interpolation == "step") return a.value
    val t = (time - a.time) / (b.time - a.time)
    if (a.interpolation != "catmullrom" && b.interpolation != "catmullrom")
        return a.value * (1 - t) + b.value * t
    val p0 =
        keys.getOrNull(before - 1)?.value
            ?: if (loop && keys.size >= 3) keys[keys.lastIndex - 1].value else a.value
    val p3 =
        keys.getOrNull(before + 2)?.value ?: if (loop && keys.size >= 3) keys[1].value else b.value
    fun c(v0: Double, v1: Double, v2: Double, v3: Double): Double =
        (2 * v1 +
            (-v0 + v2) * t +
            (2 * v0 - 5 * v1 + 4 * v2 - v3) * t * t +
            (-v0 + 3 * v1 - 3 * v2 + v3) * t * t * t) / 2
    return NpcVector3(
        c(p0.x, a.value.x, b.value.x, p3.x),
        c(p0.y, a.value.y, b.value.y, p3.y),
        c(p0.z, a.value.z, b.value.z, p3.z),
    )
}

val ZERO = NpcVector3(0.0, 0.0, 0.0)
val ONE = NpcVector3(1.0, 1.0, 1.0)

operator fun NpcVector3.plus(other: NpcVector3) = NpcVector3(x + other.x, y + other.y, z + other.z)

operator fun NpcVector3.minus(other: NpcVector3) = NpcVector3(x - other.x, y - other.y, z - other.z)

operator fun NpcVector3.times(scale: Double) = NpcVector3(x * scale, y * scale, z * scale)
