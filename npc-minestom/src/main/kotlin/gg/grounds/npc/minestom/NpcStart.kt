package gg.grounds.npc.minestom

import java.util.concurrent.CompletionStage

/**
 * Owns a shared NPC from preparation through final cleanup. Retain this token before observing
 * readiness, and include cancel completion in the host shutdown drain.
 */
public interface NpcStart {
    /**
     * Observe hidden preparation as Ready or Rejected without transferring cancellation ownership.
     * Ready does not mean visible; commands are queued and must be observed.
     */
    public fun ready(): CompletionStage<NpcPrepareResult>

    /**
     * Idempotently cancel preparation/queued commands and drain parts, private copies and accepted
     * I/O. No additional scheduler tick is required; failures complete this stage exceptionally.
     */
    public fun cancel(): CompletionStage<Void>
}
