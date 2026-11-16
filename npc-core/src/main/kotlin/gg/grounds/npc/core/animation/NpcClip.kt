package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableMap

/** Numeric animation with a finite positive duration in seconds, at most 300; tracks reference model bone IDs. */
class NpcClip(val loop: NpcLoopMode, val lengthSeconds: Double, tracks: Map<String, NpcBoneTrack>) {
    /** Bone IDs mapped to immutable channel tracks. Model validation verifies each ID. */
    val tracks: Map<String, NpcBoneTrack> = immutableMap(tracks)
}
