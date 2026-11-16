package gg.grounds.npc.core.io

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem

/**
 * A fully validated model or nonempty immutable diagnostics. Callers must handle both variants
 * before creating a renderer.
 *
 * @property model Validated model definition, or namespaced item-model ID for a mesh part.
 * @property problems Nonempty immutable failure diagnostics.
 */
public sealed interface NpcDecodeResult {
    /**
     * Complete validated numeric model.
     *
     * @property model Immutable usable model definition.
     */
    public data class Success(val model: NpcModel) : NpcDecodeResult

    /**
     * Nonempty immutable authored-input diagnostics; no partially valid model escapes.
     *
     * @property problems Stable field diagnostics describing the input error.
     */
    public class Failure(problems: Collection<NpcProblem>) : NpcDecodeResult {
        /** Nonempty immutable failure diagnostics. */
        public val problems: List<NpcProblem> = immutableList(problems)

        init {
            require(this.problems.isNotEmpty())
        }
    }
}
