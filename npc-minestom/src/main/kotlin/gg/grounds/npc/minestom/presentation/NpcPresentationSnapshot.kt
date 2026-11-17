package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot

/**
 * Immutable diagnostics of the last sampled personal pose and channel ownership. Snapshot access
 * does not read the clock or send sound.
 *
 * @property anchor Current immutable root/head-anchor snapshot.
 * @property phase Current private-group lifecycle phase.
 * @property clipMode Whether the clip follows the shared sample or is private.
 * @property headMode Whether the head follows the shared sample or is private.
 * @property clipId Last sampled clip ID, or null for the rest pose.
 * @property clipSeconds Last sampled clip-local time in seconds; no clock read occurs on snapshot.
 */
public data class NpcPresentationSnapshot(
    /** Current immutable root/head-anchor snapshot. */
    val anchor: NpcAnchorSnapshot,
    /** Current private-group lifecycle phase. */
    val phase: NpcPresentationPhase,
    /** Whether the clip follows the shared sample or is private. */
    val clipMode: NpcPresentationChannel,
    /** Whether the head follows the shared sample or is private. */
    val headMode: NpcPresentationChannel,
    /** Last sampled clip ID, or null for the rest pose. */
    val clipId: String?,
    /** Last sampled clip-local time in seconds; no clock read occurs on snapshot. */
    val clipSeconds: Double,
)
