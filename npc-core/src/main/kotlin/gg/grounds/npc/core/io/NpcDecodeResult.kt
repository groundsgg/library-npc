package gg.grounds.npc.core.io

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem

/** Complete valid model or nonempty immutable diagnostics; a failure never exposes a partial model. */
sealed interface NpcDecodeResult {
    data class Success(val model: NpcModel) : NpcDecodeResult

    class Failure(problems: Collection<NpcProblem>) : NpcDecodeResult {
        val problems = immutableList(problems)

        init {
            require(this.problems.isNotEmpty())
        }
    }
}
