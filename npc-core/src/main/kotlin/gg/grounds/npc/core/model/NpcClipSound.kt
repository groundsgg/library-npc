package gg.grounds.npc.core.model

/** A namespaced sound cue with positive volume, pitch and radius in world blocks; emitted by an explicit clip start. */
data class NpcClipSound(val key: String, val volume: Double, val pitch: Double, val radius: Double)
