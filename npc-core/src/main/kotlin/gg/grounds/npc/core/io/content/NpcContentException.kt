package gg.grounds.npc.core.io.content

import gg.grounds.npc.core.validation.NpcProblem
import java.util.Collections

/** Authored content failure; [problems] is an immutable, nonempty diagnostic list. */
public class NpcContentException(problems: List<NpcProblem>) :
    IllegalArgumentException(problems.joinToString("; ") { "${it.path}: ${it.message}" }) {
    /** Stable codes, resource/field paths and explanations. */
    public val problems: List<NpcProblem> = Collections.unmodifiableList(problems.toList())

    init {
        require(problems.isNotEmpty())
    }
}
