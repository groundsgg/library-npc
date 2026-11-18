package gg.grounds.npc.importer.config

import java.nio.file.Path

/** Immutable import inputs; building options performs no IO or source discovery. */
public class NpcImportOptions private constructor(builder: Builder) {
    /** File or directory selected by the application. */
    public val input: Path = builder.input.toAbsolutePath().normalize()
    /** Namespace used by default model identities. */
    public val namespace: String = builder.namespace
    /** Tested Minecraft client profile. */
    public val target: NpcMinecraftTarget = builder.target
    /** Optional existing pack directory/ZIP, read without modification. */
    public val basePack: Path? = builder.base?.toAbsolutePath()?.normalize()
    /** Optional schema-1 identity/sound configuration file. */
    public val config: Path? = builder.configuration?.toAbsolutePath()?.normalize()
    /** Optional pack description override. */
    public val description: String? = builder.text

    /** Application-owned builder with named optional inputs. */
    public class Builder
    internal constructor(
        internal val input: Path,
        internal val namespace: String,
        internal val target: NpcMinecraftTarget,
    ) {
        internal var base: Path? = null
        internal var configuration: Path? = null
        internal var text: String? = null

        /** Select an existing pack to merge; it remains unchanged. */
        public fun basePack(path: Path): Builder = apply { base = path }

        /** Select explicit model identities and sound bindings. */
        public fun config(path: Path): Builder = apply { configuration = path }

        /** Replace the generated/base description explicitly. */
        public fun description(description: String): Builder = apply { text = description }

        /** Validate options and take an immutable snapshot. */
        public fun build(): NpcImportOptions {
            require(namespace.matches(Regex("[a-z0-9_.-]+"))) { "Invalid namespace: $namespace" }
            return NpcImportOptions(this)
        }
    }

    /** Java-friendly entrypoint for required inputs. */
    public companion object {
        /** Create options for a source, default namespace and tested client target. */
        @JvmStatic
        public fun builder(input: Path, namespace: String, target: NpcMinecraftTarget): Builder =
            Builder(input, namespace, target)
    }
}
