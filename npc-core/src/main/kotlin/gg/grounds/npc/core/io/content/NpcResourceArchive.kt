package gg.grounds.npc.core.io.content

import gg.grounds.npc.core.internal.content.ContentFiles
import java.nio.file.Path
import java.util.Collections

/** Shared bounded resource-directory/ZIP input for content pipelines and pack composition. */
public object NpcResourceArchive {
    /**
     * Read safe unique relative resources, rejecting symlinks and expanded-byte/entry overflow.
     * Returns an immutable lookup with fresh caller-owned byte buffers, without retaining them.
     * Limits may be tightened up to the hard 256 MiB / 8192-entry maxima.
     *
     * @throws NpcContentException for unsafe/oversized resources.
     * @throws java.io.IOException for filesystem/archive IO failures.
     */
    @JvmStatic
    @JvmOverloads
    @Throws(java.io.IOException::class)
    public fun read(
        path: Path,
        maxBytes: Int = 256 * 1024 * 1024,
        maxEntries: Int = 8192,
    ): Map<String, ByteArray> {
        require(maxBytes in 1..256 * 1024 * 1024 && maxEntries in 1..8192)
        return Collections.unmodifiableMap(ContentFiles.read(path, maxBytes, maxEntries))
    }
}
