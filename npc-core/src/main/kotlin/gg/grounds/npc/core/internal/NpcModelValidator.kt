package gg.grounds.npc.core.internal

import gg.grounds.npc.core.io.NpcDecodeLimits
import gg.grounds.npc.core.model.NpcModel

internal class NpcValidationException(val path: String, message: String) :
    IllegalArgumentException("$path: $message")

internal object NpcModelValidator {
    fun validate(model: NpcModel, limits: NpcDecodeLimits = NpcDecodeLimits()) {
        fun expect(valid: Boolean, path: String, message: String) {
            if (!valid) throw NpcValidationException(path, message)
        }
        fun id(value: String, path: String, resource: Boolean = false) {
            expect(
                if (resource)
                    value.length <= 256 && value.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                else value.length in 1..128 && value.matches(Regex("[A-Za-z0-9_.-]+")),
                path,
                "Invalid ${if(resource) "resource" else "local"} ID: $value",
            )
        }
        id(model.id, "$.asset", true)
        expect(
            model.bones.size <= limits.maxBones,
            "$.bones",
            "Too many bones; maximum ${limits.maxBones}",
        )
        expect(
            model.parts.size in 1..limits.maxParts,
            "$.parts",
            "Expected 1..${limits.maxParts} parts",
        )
        expect(
            model.animations.size <= limits.maxClips,
            "$.animations",
            "Too many clips; maximum ${limits.maxClips}",
        )
        val known = HashSet<String>()
        model.bones.forEachIndexed { i, bone ->
            id(bone.id, "$.bones[$i].id")
            expect(
                bone.parent == null || bone.parent in known,
                "$.bones[$i].parent",
                "Parent must exist before its child; cyclic references are not supported",
            )
            expect(known.add(bone.id), "$.bones[$i].id", "Duplicate bone: ${bone.id}")
        }
        model.parts.forEachIndexed { i, part ->
            expect(
                part.bone == null || part.bone in known,
                "$.parts[$i].bone",
                "Unknown bone: ${part.bone}",
            )
            id(part.model, "$.parts[$i].model", true)
        }
        model.animations.forEach { (name, clip) ->
            val path = "$.animations.$name"
            id(name, path)
            expect(
                clip.lengthSeconds.isFinite() &&
                    clip.lengthSeconds > 0 &&
                    clip.lengthSeconds <= limits.maxClipSeconds,
                "$path.length",
                "Clip duration must be finite and in (0, ${limits.maxClipSeconds}] seconds",
            )
            clip.tracks.forEach { (bone, track) ->
                val trackPath = "$path.tracks.$bone"
                expect(bone in known, trackPath, "Unknown track bone: $bone")
                track.channels().forEach { (channel, keys) ->
                    val channelPath = "$trackPath.$channel"
                    expect(
                        keys.size <= limits.maxKeysPerTrack,
                        channelPath,
                        "Too many keys; maximum ${limits.maxKeysPerTrack}",
                    )
                    keys.forEachIndexed { i, key ->
                        expect(
                            key.timeSeconds.isFinite() &&
                                key.timeSeconds >= 0 &&
                                key.timeSeconds <= clip.lengthSeconds + 1e-4,
                            "$channelPath[$i].time",
                            "Time must be finite, nonnegative and within the clip duration",
                        )
                        expect(
                            i == 0 || keys[i - 1].timeSeconds <= key.timeSeconds,
                            "$channelPath[$i].time",
                            "Key times must be sorted",
                        )
                    }
                }
            }
        }
        model.sounds.forEach { (clip, sound) ->
            val path = "$.sounds.$clip"
            expect(clip in model.animations, path, "Unknown sound clip: $clip")
            id(sound.key, "$path.key", true)
            expect(
                sound.volume.isFinite() && sound.volume > 0 && sound.volume <= 4,
                "$path.volume",
                "Volume must be finite and in (0, 4]",
            )
            expect(
                sound.pitch.isFinite() && sound.pitch > 0,
                "$path.pitch",
                "Pitch must be finite and positive",
            )
            expect(
                sound.radius.isFinite() && sound.radius > 0 && sound.radius <= 64,
                "$path.radius",
                "Radius must be finite and in (0, 64]",
            )
        }
    }
}
