package gg.grounds.npc.importer.config

/** Tested client-asset profiles; arbitrary pack-format overrides are deliberately unsupported. */
public enum class NpcMinecraftTarget(
    /** Minecraft release identifier. */
    public val version: String,
    internal val major: Int,
    internal val minor: Int,
) {
    /** Minecraft Java 26.2, resource format 88.0. */
    MINECRAFT_26_2("26.2", 88, 0),
    /** Minecraft Java 26.3, resource format 97.1. */
    MINECRAFT_26_3("26.3", 97, 1);

    /** Named profile lookup for configuration/CLI callers. */
    public companion object {
        /** Resolve an exact supported release; unknown targets throw IllegalArgumentException. */
        @JvmStatic
        public fun fromVersion(version: String): NpcMinecraftTarget =
            entries.singleOrNull { it.version == version }
                ?: throw IllegalArgumentException("Unsupported Minecraft target: $version")
    }
}
