package gg.grounds.npc.minestom.presentation

/**
 * Diagnostic lifecycle of a connection-owned private visual group. Ready means hidden;
 * closing/closed no longer accept new commands.
 */
public enum class NpcPresentationPhase {
    PREPARING,
    READY_HIDDEN,
    ACTIVE,
    CLOSING,
    CLOSED,
}
