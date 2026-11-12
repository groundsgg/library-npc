package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

data class NpcRenderTransform(val root: NpcTransform, val local: NpcTransform? = null)

data class NpcViewerState(val scaleMultiplier: Double = 1.0, val highlighted: Boolean = false) {
    init {
        require(scaleMultiplier.isFinite() && scaleMultiplier > 0 && scaleMultiplier <= 16)
    }
}

data class NpcAnchorSnapshot(val root: NpcTransform, val lookOrigin: NpcVector3?)

fun interface NpcPublicationGuard {
    /** Run the operation atomically with the host's generation-validity check. */
    fun runIfCurrent(operation: Runnable): Boolean
}

fun interface NpcMeshResolver {
    fun resolve(modelKey: String): ItemStack
}

fun interface NpcSoundSink {
    fun playNear(instance: Instance, origin: NpcVector3, sound: NpcClipSound)
}

data class NpcSpawnRequest
@JvmOverloads
constructor(
    val instance: Instance,
    val model: NpcModel,
    val transform: NpcRenderTransform,
    val publicationGuard: NpcPublicationGuard = NpcPublicationGuard {
        it.run()
        true
    },
    val meshes: NpcMeshResolver = NpcMeshResolver { key ->
        ItemStack.of(net.minestom.server.item.Material.PAPER)
            .with(net.minestom.server.component.DataComponents.ITEM_MODEL, key)
    },
    val sounds: NpcSoundSink = NpcSoundSink { _, _, _ -> },
    val config: NpcRenderConfig = NpcRenderConfig(),
)

interface NpcStart {
    fun ready(): CompletionStage<NpcPrepareResult>

    fun cancel(): CompletionStage<Void>
}

sealed interface NpcPrepareResult {
    data class Ready(val handle: NpcHandle) : NpcPrepareResult

    data class Rejected(val problems: List<NpcProblem>) : NpcPrepareResult
}

interface NpcHandle {
    fun publish(): CompletionStage<Void>

    fun applyTransform(transform: NpcRenderTransform): CompletionStage<Void>

    fun startAnimation(clipId: String, elapsedMillis: Long): CompletionStage<Void>

    fun stopAnimation(clipId: String?): CompletionStage<Void>

    fun advanceAnimation(elapsedMillis: Long): CompletionStage<Void>

    fun applyHeadLook(yaw: Double, pitch: Double): CompletionStage<Void>

    fun applyViewerState(player: Player, state: NpcViewerState): CompletionStage<Void>

    fun clearViewerState(player: Player): CompletionStage<Void>

    fun snapshot(): NpcAnchorSnapshot

    fun closeAsync(): CompletionStage<Void>
}

internal interface NpcEngineHandle : AutoCloseable {
    val headLookOrigin: NpcVector3?

    fun applyTransform(transform: NpcRenderTransform): CompletionStage<Void>

    fun startAnimation(animation: String, elapsedMillis: Long)

    fun stopAnimation(animation: String?)

    fun advanceAnimation(elapsedMillis: Long)

    fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double)

    fun applyViewerState(player: Player, state: NpcViewerState)

    fun clearViewerState(player: Player)
}
