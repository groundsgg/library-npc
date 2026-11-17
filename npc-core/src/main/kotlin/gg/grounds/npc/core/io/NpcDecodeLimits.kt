package gg.grounds.npc.core.io

/**
 * Optional stricter schema-1 decode budgets. Defaults equal hard model limits; positive custom
 * limits may only reduce those maxima. Invalid configuration throws IllegalArgumentException.
 *
 * @property maxBytes Positive UTF-8 input-byte budget; default 2 MiB.
 * @property maxBones Maximum bones, in 1..256.
 * @property maxParts Maximum parts, in 1..1024.
 * @property maxClips Maximum clips, in 1..128.
 * @property maxKeysPerTrack Maximum keys per channel track, in 1..4096.
 * @property maxClipSeconds Finite positive clip duration budget, at most 300 seconds.
 */
public data class NpcDecodeLimits
@JvmOverloads
constructor(
    /** Positive UTF-8 input-byte budget; default 2 MiB. */
    val maxBytes: Int = 2 * 1024 * 1024,
    /** Maximum bones, in 1..256. */
    val maxBones: Int = 256,
    /** Maximum parts, in 1..1024. */
    val maxParts: Int = 1024,
    /** Maximum clips, in 1..128. */
    val maxClips: Int = 128,
    /** Maximum keys per channel track, in 1..4096. */
    val maxKeysPerTrack: Int = 4096,
    /** Finite positive clip duration budget, at most 300 seconds. */
    val maxClipSeconds: Double = 300.0,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxBones in 1..256) { "maxBones must be in 1..256" }
        require(maxParts in 1..1024) { "maxParts must be in 1..1024" }
        require(maxClips in 1..128) { "maxClips must be in 1..128" }
        require(maxKeysPerTrack in 1..4096) { "maxKeysPerTrack must be in 1..4096" }
        require(maxClipSeconds.isFinite() && maxClipSeconds > 0 && maxClipSeconds <= 300) {
            "maxClipSeconds must be finite and in (0, 300]"
        }
    }
}
