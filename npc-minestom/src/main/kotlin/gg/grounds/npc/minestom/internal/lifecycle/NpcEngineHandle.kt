package gg.grounds.npc.minestom.internal.lifecycle

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

internal interface NpcEngineHandle : AutoCloseable {
    val headLookOrigin: NpcVector3?

    fun applyTransform(transform: NpcRenderTransform): CompletionStage<Void>

    fun playClip(animation: String, elapsedMillis: Long)

    fun stopClip(): Unit = stopClipIfPlaying(null)

    fun stopClipIfPlaying(animation: String?)

    fun advanceClipTo(elapsedMillis: Long)

    fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double)

    fun applyViewerState(player: Player, state: NpcViewerState)

    fun clearViewerState(player: Player)
}
