package gg.grounds.npc.importer.error

/**
 * A source-located authored import diagnostic.
 *
 * @property code Stable machine-readable category.
 * @property source Source filename or resource identifier.
 * @property path JSON/configuration field path.
 * @property message Human-readable reason and correction context.
 */
public data class NpcImportProblem(
    val code: String,
    val source: String,
    val path: String,
    val message: String,
)
