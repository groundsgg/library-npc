package gg.grounds.npc.minestom.spi

/**
 * Injectable nanosecond clock for personal clip playback. It must be monotonic and independent of
 * world age; the default is System.nanoTime.
 */
public fun interface NpcMonotonicClock {
    /**
     * Return nondecreasing clip-clock nanoseconds, independent of world time. Callback exceptions
     * surface through commands or scheduled presentation failure; do not block the instance
     * scheduler.
     */
    public fun nanoTime(): Long
}
