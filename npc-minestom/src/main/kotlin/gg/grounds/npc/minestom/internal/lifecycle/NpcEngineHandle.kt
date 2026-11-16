package gg.grounds.npc.minestom.internal.lifecycle

import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState

import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

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
