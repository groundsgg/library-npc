package gg.grounds.npc.core.animation

/** Playback policy; schema-1 JSON uses loop, once and hold respectively. */
public enum class NpcLoopMode {
    /** Repeat from the beginning after the authored duration. */
    LOOP,
    /** Finish once, allowing the renderer to enter its configured idle. */
    ONCE,
    /** Keep the last authored pose after the duration. */
    HOLD,
}
