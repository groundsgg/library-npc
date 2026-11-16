package gg.grounds.npc.minestom.viewer

import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

data class NpcViewerState(val scaleMultiplier: Double = 1.0, val highlighted: Boolean = false) {
    init {
        require(scaleMultiplier.isFinite() && scaleMultiplier > 0 && scaleMultiplier <= 16)
    }
}
