package gg.grounds.npc.core.model

/**
 * A namespaced explicit clip-start cue. The containing model validates volume in (0, 4], positive
 * pitch and radius in (0, 64] world blocks.
 *
 * @property key Namespaced client sound ID.
 * @property volume Finite sound volume in (0, 4].
 * @property pitch Finite positive playback pitch.
 * @property durationMillis Effective audio duration at pitch one, 0 when unknown; at most 300000
 *   ms.
 * @property radius Finite spatial reach in (0, 64] world blocks.
 */
public data class NpcClipSound
@JvmOverloads
constructor(
    val key: String,
    val volume: Double,
    val pitch: Double,
    val radius: Double,
    val durationMillis: Long = 0,
)
