package gg.grounds.npc.core.io.content

import gg.grounds.npc.core.internal.content.ContentBundleReader
import java.nio.file.Path

/** Loads bounded, complete schema-1 content directories or ZIPs without the importer. */
public object NpcContentLoader {
    /**
     * Load an immutable snapshot and verify its identity, model hashes and schema-1 definitions. No
     * global registry is modified. Unknown payload files and duplicate IDs are rejected.
     *
     * @throws NpcContentException for invalid authored content.
     * @throws java.io.IOException if the configured input cannot be read.
     */
    @JvmStatic
    @Throws(java.io.IOException::class)
    public fun load(path: Path): NpcContent = ContentBundleReader.load(path)

    /**
     * Load a defensive bounded snapshot of bundle-relative files, for in-memory build pipelines.
     * Applies the same contract, checksum and model checks as directory/ZIP loading.
     *
     * @throws NpcContentException for invalid or oversized resource maps.
     */
    @JvmStatic
    public fun load(resources: Map<String, ByteArray>): NpcContent =
        ContentBundleReader.load(resources)
}
