package gg.grounds.npc.minestom

import java.util.concurrent.CompletionStage

/** An exclusive, single-use shared clip start. Close unused leases and after requesting a start. */
public interface NpcClipReservation : AutoCloseable {
    /**
     * Queue the admitted clip at a nonnegative absolute time; the lease survives until it drains.
     */
    public fun start(elapsedMillis: Long): CompletionStage<Void>

    /** Release an unused lease, or release after an already queued start completes. */
    public override fun close()
}
