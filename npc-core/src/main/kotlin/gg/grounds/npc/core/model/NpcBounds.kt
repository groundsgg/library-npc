package gg.grounds.npc.core.model

import gg.grounds.npc.core.math.NpcVector3

/** Model-local centre and strictly positive size in authored units. */
data class NpcBounds(val center: NpcVector3, val size: NpcVector3) {
    init {
        require(size.x > 0 && size.y > 0 && size.z > 0) { "Bounds must be positive" }
    }
}
