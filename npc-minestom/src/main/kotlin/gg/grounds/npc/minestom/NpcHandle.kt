package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot
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
