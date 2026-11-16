package gg.grounds.npc.minestom.presentation

import java.util.concurrent.CompletionStage

/**
 * One concrete viewer’s private visual group. Commands run on the owning instance scheduler and
 * return failure stages. Private clips use their own monotonic clock; root movement and scale/glow
 * follow the shared parent. Closing drains copies before restoring the current common pose for a
 * still-valid connection.
 */
public interface NpcPresentationHandle {
    /**
     * Queue guarded activation of the hidden group and suppress the common NPC only for its
     * concrete viewer. Repeated activation is harmless; denied generation or invalid ownership
     * fails and cleans up.
     */
    public fun publish(): CompletionStage<Void>

    /**
     * Override only the personal clip channel and start its monotonic local clock at zero. Unknown
     * clip IDs fail the stage; only explicit starts emit the configured private cue.
     */
    public fun playClip(clipId: String): CompletionStage<Void>

    /**
     * Enter configured personal idle or rest, retaining private clip ownership. Does not replay a
     * cue or release the head channel.
     */
    public fun stopClip(): CompletionStage<Void>

    /**
     * Release the private clip channel to the current actual shared sample, including
     * seek/pause/stop; does not replay sound.
     */
    public fun followSharedClip(): CompletionStage<Void>

    /**
     * Override only this viewer’s head channel using finite yaw/pitch degrees. Root position and
     * body pose continue following the parent.
     */
    public fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double): CompletionStage<Void>

    /**
     * Release the private head channel to the current shared orientation without changing the clip
     * channel.
     */
    public fun followSharedHeadLook(): CompletionStage<Void>

    /**
     * Return immutable diagnostics of the last composed pose; never advance the clock or replay a
     * sound.
     */
    public fun snapshot(): NpcPresentationSnapshot

    /** Observe expiry or failure without initiating cancellation. */
    public fun closed(): CompletionStage<Void>

    /**
     * Idempotently remove private parts and drain accepted work before restoring the shared pose
     * for the still-valid connection. Cleanup failures complete exceptionally.
     */
    public fun closeAsync(): CompletionStage<Void>
}
