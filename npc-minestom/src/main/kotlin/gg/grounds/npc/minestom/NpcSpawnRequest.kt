package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.config.NpcRenderConfig
import gg.grounds.npc.minestom.spi.NpcMeshResolver
import gg.grounds.npc.minestom.spi.NpcPublicationGuard
import gg.grounds.npc.minestom.spi.NpcSoundSink
import gg.grounds.npc.minestom.transform.NpcRenderTransform

import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

class NpcSpawnRequest private constructor(builder: Builder) {
    val instance: Instance = builder.instance
    val model: NpcModel = builder.model
    val transform: NpcRenderTransform = builder.transform
    val publicationGuard: NpcPublicationGuard = builder.publicationGuard
    val meshes: NpcMeshResolver = builder.meshes
    val sounds: NpcSoundSink = builder.sounds
    val config: NpcRenderConfig = builder.config

    

    fun toBuilder(): Builder = builder(instance, model, transform).publicationGuard(publicationGuard).meshes(meshes).sounds(sounds).config(config)

    companion object {
        @JvmStatic
        fun builder(instance: Instance, model: NpcModel, transform: NpcRenderTransform): Builder = Builder(instance, model, transform)
    }

    /** Mutable construction helper; confine a builder to one thread. Built options are immutable. */
    class Builder internal constructor(internal var instance: Instance, internal var model: NpcModel, internal var transform: NpcRenderTransform) {
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
        fun instance(value: Instance): Builder = apply { instance = value }
        fun model(value: NpcModel): Builder = apply { model = value }
        fun transform(value: NpcRenderTransform): Builder = apply { transform = value }
        fun publicationGuard(value: NpcPublicationGuard): Builder = apply { publicationGuard = value }
        fun meshes(value: NpcMeshResolver): Builder = apply { meshes = value }
        fun sounds(value: NpcSoundSink): Builder = apply { sounds = value }
        fun config(value: NpcRenderConfig): Builder = apply { config = value }
        fun build(): NpcSpawnRequest = NpcSpawnRequest(this)
    }
}
