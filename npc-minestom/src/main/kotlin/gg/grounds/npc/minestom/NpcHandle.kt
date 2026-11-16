package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.presentation.NpcPresentationOptions
import gg.grounds.npc.minestom.presentation.NpcPresentationStart
import gg.grounds.npc.minestom.transform.NpcAnchorSnapshot
import gg.grounds.npc.minestom.transform.NpcRenderTransform
import gg.grounds.npc.minestom.viewer.NpcViewerState
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

/**
 * A prepared shared NPC. Retain its start token until asynchronous cleanup completes. Commands are
 * queued on the owning instance scheduler; every command failure is delivered through its returned
 * stage. Completion describes server work, not client rendering. Handles belong to this backend and
 * may not be transferred across proxies or player connections.
 */
interface NpcHandle {
    /** Publish hidden parts through the host generation guard. A denied guard cancels ownership. */
    fun publish(): CompletionStage<Void>

    /**
     * Relocate the root and wait for destination loading, parts and owned private copies to drain.
     */
    fun applyTransform(transform: NpcRenderTransform): CompletionStage<Void>

    /**
     * Start a shared clip at a nonnegative absolute clip time in milliseconds; host advances its
     * clock.
     */
    fun playClip(clipId: String, elapsedMillis: Long): CompletionStage<Void>

    /** Start a shared clip at zero milliseconds. Does not install an automatic clock driver. */
    fun playClip(clipId: String): CompletionStage<Void> = playClip(clipId, 0)

    /** Stop the requested shared clip and render the bind pose. */
    fun stopClip(): CompletionStage<Void>

    /** Stop only if this ID is the currently requested shared clip; otherwise do nothing. */
    fun stopClipIfPlaying(clipId: String): CompletionStage<Void>

    /**
     * Seek to a nonnegative absolute clip time in milliseconds, including backwards seeks; not a
     * delta.
     */
    fun advanceClipTo(elapsedMillis: Long): CompletionStage<Void>

    /**
     * Apply additive model-local head orientation. Angles are finite degrees; body pose is
     * unaffected.
     */
    fun applyHeadLook(yawDegrees: Double, pitchDegrees: Double): CompletionStage<Void>

    /** Set scale/glow for this concrete online connection in the owning instance. */
    fun applyViewerState(player: Player, state: NpcViewerState): CompletionStage<Void>

    /** Remove overrides belonging to this connection and restore the shared visual state. */
    fun clearViewerState(player: Player): CompletionStage<Void>

    /**
     * Prepare a private visual copy for one concrete viewer; retain its token before observing
     * ready.
     */
    fun preparePresentation(options: NpcPresentationOptions): NpcPresentationStart

    /** Thread-safe immutable root/head-anchor diagnostic snapshot; does not advance animation. */
    fun snapshot(): NpcAnchorSnapshot

    /** Observe final cleanup or failure without initiating cancellation. */
    fun closed(): CompletionStage<Void>

    /**
     * Idempotently cancel queued work and drain owned parts/copies/I/O, without another scheduler
     * tick.
     */
    fun closeAsync(): CompletionStage<Void>
}
