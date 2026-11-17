package gg.grounds.npc.importer.model

import java.nio.file.Path

/** Paths of a successfully verified and atomically published compilation. */
public class NpcImportOutput
internal constructor(
    /** Prepared server bundle; can be loaded without the importer. */
    public val serverContent: Path,
    /** Complete resourcepack including any selected base. */
    public val resourcePack: Path,
    /** Generated-only client files for an external pack composer. */
    public val packFragment: Path,
    /** Receipt binding content and final archive hashes. */
    public val manifest: Path,
    /** Identity of the generated models and expected client contributions. */
    public val contentId: String,
)
