package gg.grounds.npc.minestom

import gg.grounds.npc.core.validation.NpcProblem

/**
 * Preparation outcome. Ready parts are still hidden; only a successful guarded publish makes them
 * visible. Rejection never yields a usable partial handle.
 *
 * @property handle Prepared handle that remains hidden until guarded publish succeeds.
 * @property problems Nonempty immutable failure diagnostics.
 */
public sealed interface NpcPrepareResult {
    /**
     * Hidden prepared handle. The owner must still publish it and retain its start for cleanup.
     *
     * @property handle Hidden prepared handle.
     */
    public data class Ready(val handle: NpcHandle) : NpcPrepareResult

    /**
     * Preparation was rejected before activation.
     *
     * @property problems Stable diagnostics describing the rejection.
     */
    public data class Rejected(val problems: List<NpcProblem>) : NpcPrepareResult
}
