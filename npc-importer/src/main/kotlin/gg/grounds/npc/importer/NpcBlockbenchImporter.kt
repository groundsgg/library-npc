package gg.grounds.npc.importer

import gg.grounds.npc.importer.internal.blockbench.BlockbenchReader
import gg.grounds.npc.importer.model.NpcImportedModel
import java.nio.file.Path

/** Synchronous standalone reader for supported generic Blockbench 5.0/legacy 4.10 projects. */
public object NpcBlockbenchImporter {
    /**
     * Import one model with an explicit namespaced identity. Relative texture references must stay
     * inside the source directory. No network, scripts, server objects or global registry are used.
     *
     * @throws gg.grounds.npc.importer.error.NpcImportException for authored/unsupported content.
     * @throws java.io.IOException if the source itself cannot be read.
     */
    @JvmStatic
    @Throws(java.io.IOException::class)
    public fun importModel(source: Path, id: String): NpcImportedModel =
        BlockbenchReader.read(source, id, source.toAbsolutePath().normalize().parent).let {
            NpcImportedModel(it.model, it.modelJson, it.resources)
        }
}
