// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom.internal.audience

import gg.grounds.npc.minestom.config.NpcRenderConfig

/** Shared identities avoid boxing millisecond values in per-viewer membership maps. */
internal enum class NpcViewerRate(val interval: Long) {
    NEAR(200L),
    MID(250L),
    FAR(500L);

    fun interval(config: NpcRenderConfig): Long =
        when (this) {
            NEAR -> config.nearIntervalMillis
            MID -> config.midIntervalMillis
            FAR -> config.farIntervalMillis
        }
}
