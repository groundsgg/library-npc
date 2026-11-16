package gg.grounds.npc.minestom.internal.presentation

import gg.grounds.npc.minestom.presentation.NpcPresentationOptions
import gg.grounds.npc.minestom.presentation.NpcPresentationStart

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

internal interface NpcPresentationParent {
    fun preparePresentation(options: NpcPresentationOptions): NpcPresentationStart
}
