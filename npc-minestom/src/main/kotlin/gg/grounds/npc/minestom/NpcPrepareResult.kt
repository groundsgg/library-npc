package gg.grounds.npc.minestom

import gg.grounds.npc.core.validation.NpcProblem
import java.util.Collections

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
    public class Rejected(problems: List<NpcProblem>) : NpcPrepareResult {
        /** Nonempty immutable snapshot of the rejection diagnostics. */
        public val problems: List<NpcProblem> = Collections.unmodifiableList(ArrayList(problems))

        init {
            require(this.problems.isNotEmpty()) { "Rejection requires at least one problem" }
        }

        /** Describe the stable rejection diagnostics for logs. */
        override fun toString(): String = "Rejected(problems=$problems)"
    }
}
