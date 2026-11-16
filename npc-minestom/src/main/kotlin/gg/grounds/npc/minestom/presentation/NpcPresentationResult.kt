package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.core.validation.NpcProblem

/**
 * Hidden private preparation outcome. Rejected groups never hide the common NPC; a Ready handle
 * must still be published.
 *
 * @property handle Prepared handle that remains hidden until guarded publish succeeds.
 * @property problems Nonempty immutable failure diagnostics.
 */
public sealed interface NpcPresentationResult {
    /**
     * Hidden prepared handle. The owner must still publish it and retain its start for cleanup.
     *
     * @property handle Hidden prepared handle.
     */
    public data class Ready(val handle: NpcPresentationHandle) : NpcPresentationResult

    /**
     * Preparation was rejected before activation.
     *
     * @property problems Stable diagnostics describing the rejection.
     */
    public data class Rejected(val problems: List<NpcProblem>) : NpcPresentationResult
}
