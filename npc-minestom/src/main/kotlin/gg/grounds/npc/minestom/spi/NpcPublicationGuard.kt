package gg.grounds.npc.minestom.spi

fun interface NpcPublicationGuard {
    /** Run the operation atomically with the host's generation-validity check. */
    fun runIfCurrent(operation: Runnable): Boolean
}
