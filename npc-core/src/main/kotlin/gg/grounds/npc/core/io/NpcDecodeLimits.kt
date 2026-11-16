package gg.grounds.npc.core.io

/** Optional stricter schema-1 decode budgets. Defaults equal the supported hard limits; budgets cannot exceed them. */
data class NpcDecodeLimits
@JvmOverloads
constructor(
    val maxBytes: Int = 2 * 1024 * 1024,
    val maxBones: Int = 256,
    val maxParts: Int = 1024,
    val maxClips: Int = 128,
    val maxKeysPerTrack: Int = 4096,
    val maxClipSeconds: Double = 300.0,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxBones in 1..256) { "maxBones must be in 1..256" }
        require(maxParts in 1..1024) { "maxParts must be in 1..1024" }
        require(maxClips in 1..128) { "maxClips must be in 1..128" }
        require(maxKeysPerTrack in 1..4096) { "maxKeysPerTrack must be in 1..4096" }
        require(maxClipSeconds.isFinite() && maxClipSeconds > 0 && maxClipSeconds <= 300) { "maxClipSeconds must be finite and in (0, 300]" }
    }
}
