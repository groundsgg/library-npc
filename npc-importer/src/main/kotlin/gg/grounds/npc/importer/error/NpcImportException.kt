package gg.grounds.npc.importer.error

import java.util.Collections

/** An authored import/compilation failure with immutable nonempty [problems]. */
public class NpcImportException(problems: List<NpcImportProblem>) :
    IllegalArgumentException(
        problems.joinToString("; ") { "${it.source}:${it.path}: ${it.message}" }
    ) {
    /** Structured source-located diagnostics, safe to retain. */
    public val problems: List<NpcImportProblem> = Collections.unmodifiableList(problems.toList())

    init {
        require(problems.isNotEmpty())
    }
}
