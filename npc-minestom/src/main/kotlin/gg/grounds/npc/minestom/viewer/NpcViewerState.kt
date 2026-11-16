package gg.grounds.npc.minestom.viewer

data class NpcViewerState(val scaleMultiplier: Double = 1.0, val highlighted: Boolean = false) {
    init {
        require(scaleMultiplier.isFinite() && scaleMultiplier > 0 && scaleMultiplier <= 16)
    }
}
