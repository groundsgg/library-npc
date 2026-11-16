package gg.grounds.npc.minestom.spi

/**
 * Host generation gate for atomic publication. Implementations must check currency and execute the
 * supplied operation as one synchronous action.
 */
public fun interface NpcPublicationGuard {
    /** Run the operation atomically with the host's generation-validity check. */
    public fun runIfCurrent(operation: Runnable): Boolean
}
