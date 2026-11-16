package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

data class NpcPresentationLimits
@JvmOverloads
constructor(
    val maxPresentations: Int = 32,
    val maxAdditionalParts: Int = 1024,
) {
    init {
        require(maxPresentations > 0 && maxAdditionalParts > 0)
    }
}

