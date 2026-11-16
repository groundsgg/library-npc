package gg.grounds.npc.minestom.presentation

import java.util.concurrent.CompletionStage

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
