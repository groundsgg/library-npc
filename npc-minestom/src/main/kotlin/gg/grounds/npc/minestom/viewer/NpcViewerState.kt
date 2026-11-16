package gg.grounds.npc.minestom.viewer

/**
 * Scale and glow override for one concrete connection. Scale must be finite and in (0, 16]; account
 * UUIDs do not transfer this state to another login.
 *
 * @property scaleMultiplier Finite per-connection scale in (0, 16]; default 1.
 * @property highlighted Per-connection glow flag; default false.
 */
public data class NpcViewerState(
    val scaleMultiplier: Double = 1.0,
    val highlighted: Boolean = false,
) {
    init {
        require(scaleMultiplier.isFinite() && scaleMultiplier > 0 && scaleMultiplier <= 16)
    }
}
