package gg.grounds.npc.importer

import gg.grounds.npc.importer.config.NpcImportOptions
import gg.grounds.npc.importer.error.*
import gg.grounds.npc.importer.internal.bundle.*
import gg.grounds.npc.importer.model.NpcImportOutput
import java.nio.file.Path

/**
 * One synchronous import/validation/pack pipeline shared by applications and the standalone CLI.
 */
public object NpcModelImporter {
    /** Version embedded in this importer artifact, recorded in content provenance. */
    @JvmStatic public fun version(): String = ImporterVersion.value

    /**
     * Validate a complete import and optional pack merge entirely in memory. Writes no output.
     * Returns immutable authored diagnostics; an empty list means compilation can proceed.
     *
     * @throws java.io.IOException if a configured input cannot be read.
     */
    @JvmStatic
    @Throws(java.io.IOException::class)
    public fun validate(options: NpcImportOptions): List<NpcImportProblem> =
        try {
            NpcCompilation.prepare(options)
            emptyList()
        } catch (e: NpcImportException) {
            e.problems
        }

    /**
     * Compile matching server/client content into a new directory. Existing outputs and all inputs
     * remain untouched. Invalid content never publishes a partial result.
     *
     * @throws NpcImportException for authored input, merge or output-conflict failures.
     * @throws java.io.IOException for filesystem failures.
     */
    @JvmStatic
    @Throws(java.io.IOException::class)
    public fun compile(options: NpcImportOptions, output: Path): NpcImportOutput =
        CompilationWriter.write(NpcCompilation.prepare(options), output)
}
