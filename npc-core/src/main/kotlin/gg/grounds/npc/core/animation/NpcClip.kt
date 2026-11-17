package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableMap

/**
 * Immutable animation with finite positive duration, at most 300 seconds. The containing model
 * validates bone IDs, ordered key times and channel budgets. No expressions are evaluated.
 *
 * @property loop Typed repeat/one-shot/hold policy; schema-1 wire names remain unchanged.
 * @property lengthSeconds Finite duration in (0, 300] seconds.
 * @property tracks Bone IDs mapped to immutable numeric channel tracks.
 */
public class NpcClip(
    public val loop: NpcLoopMode,
    public val lengthSeconds: Double,
    tracks: Map<String, NpcBoneTrack>,
) {
    /** Bone IDs mapped to immutable channel tracks. Model validation verifies each ID. */
    public val tracks: Map<String, NpcBoneTrack> = immutableMap(tracks)
}
