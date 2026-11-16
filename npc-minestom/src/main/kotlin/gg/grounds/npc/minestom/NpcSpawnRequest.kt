package gg.grounds.npc.minestom

import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.minestom.config.NpcRenderConfig
import gg.grounds.npc.minestom.spi.NpcMeshResolver
import gg.grounds.npc.minestom.spi.NpcPublicationGuard
import gg.grounds.npc.minestom.spi.NpcSoundSink
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

/**
 * Immutable shared NPC inputs. Use builder with an owning instance, validated model and root
 * transform. Default meshes use PAPER plus the item-model component; the default sound sink is
 * silent.
 *
 * @property instance Owning Minestom instance; live state belongs to this backend only.
 * @property model Complete validated immutable numeric model to render.
 * @property transform Shared world/root and optional local model transform.
 * @property publicationGuard Synchronous atomic host generation check; the default runs the
 *   operation immediately.
 * @property meshes Namespaced mesh resolver; defaults to PAPER plus the item-model component.
 * @property sounds Shared spatial cue delivery; defaults to a silent sink.
 * @property config Visibility/update policy; defaults preserve the established shared render
 *   profile.
 */
public class NpcSpawnRequest private constructor(builder: Builder) {
    /** Owning Minestom instance; live state belongs to this backend only. */
    public val instance: Instance = builder.instance
    /** Complete validated immutable numeric model to render. */
    public val model: NpcModel = builder.model
    /** Shared world/root and optional local model transform. */
    public val transform: NpcRenderTransform = builder.transform
    /** Synchronous atomic host generation check; the default runs the operation immediately. */
    public val publicationGuard: NpcPublicationGuard = builder.publicationGuard
    /** Namespaced mesh resolver; defaults to PAPER plus the item-model component. */
    public val meshes: NpcMeshResolver = builder.meshes
    /** Shared spatial cue delivery; defaults to a silent sink. */
    public val sounds: NpcSoundSink = builder.sounds
    /** Visibility/update policy; defaults preserve the established shared render profile. */
    public val config: NpcRenderConfig = builder.config

    /**
     * Copy these immutable options into an independent mutable builder. Later changes cannot mutate
     * this instance.
     */
    public fun toBuilder(): Builder =
        builder(instance, model, transform)
            .publicationGuard(publicationGuard)
            .meshes(meshes)
            .sounds(sounds)
            .config(config)

    /**
     * Shared factories and constants; Java entrypoints are exposed with their documented static
     * annotations.
     */
    public companion object {
        /**
         * Create a mutable construction helper with the required identity/ownership inputs.
         * Defaults are applied at build; confine the builder to one thread.
         */
        @JvmStatic
        public fun builder(
            instance: Instance,
            model: NpcModel,
            transform: NpcRenderTransform,
        ): Builder = Builder(instance, model, transform)
    }

    /**
     * Mutable construction helper; confine a builder to one thread. Built options are immutable.
     */
    public class Builder
    internal constructor(
        internal var instance: Instance,
        internal var model: NpcModel,
        internal var transform: NpcRenderTransform,
    ) {
        internal var publicationGuard: NpcPublicationGuard = NpcPublicationGuard {
            it.run()
            true
        }
        internal var meshes: NpcMeshResolver = NpcMeshResolver { key ->
            ItemStack.of(net.minestom.server.item.Material.PAPER)
                .with(net.minestom.server.component.DataComponents.ITEM_MODEL, key)
        }
        internal var sounds: NpcSoundSink = NpcSoundSink { _, _, _ -> }
        internal var config: NpcRenderConfig = NpcRenderConfig.builder().build()

        /**
         * Configure Owning Minestom instance; live state belongs to this backend only. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun instance(value: Instance): Builder = apply { instance = value }

        /**
         * Configure Validated model definition, or namespaced item-model ID for a mesh part.
         * Validation occurs at build; returns this builder for chaining.
         */
        public fun model(value: NpcModel): Builder = apply { model = value }

        /**
         * Configure Shared world/root and optional local model transform. Validation occurs at
         * build; returns this builder for chaining.
         */
        public fun transform(value: NpcRenderTransform): Builder = apply { transform = value }

        /**
         * Configure Synchronous atomic host generation check; the default runs the operation
         * immediately. Validation occurs at build; returns this builder for chaining.
         */
        public fun publicationGuard(value: NpcPublicationGuard): Builder = apply {
            publicationGuard = value
        }

        /**
         * Configure Namespaced mesh resolver; defaults to PAPER plus the item-model component.
         * Validation occurs at build; returns this builder for chaining.
         */
        public fun meshes(value: NpcMeshResolver): Builder = apply { meshes = value }

        /**
         * Configure Immutable cues keyed by existing clip IDs. Validation occurs at build; returns
         * this builder for chaining.
         */
        public fun sounds(value: NpcSoundSink): Builder = apply { sounds = value }

        /**
         * Configure Visibility/update policy; defaults preserve the established shared render
         * profile. Validation occurs at build; returns this builder for chaining.
         */
        public fun config(value: NpcRenderConfig): Builder = apply { config = value }

        /**
         * Validate the configured values and create independent immutable options. Invalid ranges
         * or relationships throw IllegalArgumentException with the property name.
         */
        public fun build(): NpcSpawnRequest = NpcSpawnRequest(this)
    }
}
