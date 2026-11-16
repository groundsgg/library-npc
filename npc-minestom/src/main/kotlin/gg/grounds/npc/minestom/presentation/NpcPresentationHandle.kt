package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

interface NpcPresentationHandle {
    fun publish(): CompletionStage<Void>

    fun playClip(clipId: String): CompletionStage<Void>

    fun stopClip(): CompletionStage<Void>

    fun followSharedClip(): CompletionStage<Void>

    fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double): CompletionStage<Void>

    fun followSharedHeadLook(): CompletionStage<Void>

    fun snapshot(): NpcPresentationSnapshot

    /** Observe expiry or failure without initiating cancellation. */
    fun closed(): CompletionStage<Void>

    fun closeAsync(): CompletionStage<Void>
}
