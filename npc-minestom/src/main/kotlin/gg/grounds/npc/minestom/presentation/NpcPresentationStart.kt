package gg.grounds.npc.minestom.presentation

import java.util.concurrent.CompletionStage

/**
 * Owns a private visual reservation from preparation onward. Retain it immediately and drain cancel
 * even when readiness has not completed.
 */
public interface NpcPresentationStart {
    /**
     * Observe private hidden preparation as Ready or Rejected. Retain this start for cancellation
     * even after a Ready handle is delivered.
     */
    public fun ready(): CompletionStage<NpcPresentationResult>

    /**
     * Idempotently release this reservation and drain hidden/active private parts. Restore the
     * latest shared state only for the still-valid parent and concrete connection.
     */
    public fun cancel(): CompletionStage<Void>
}
