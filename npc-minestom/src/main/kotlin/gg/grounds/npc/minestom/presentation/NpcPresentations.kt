package gg.grounds.npc.minestom.presentation

import gg.grounds.npc.minestom.NpcHandle
import gg.grounds.npc.minestom.internal.presentation.NpcPresentationParent
import gg.grounds.npc.minestom.internal.presentation.rejectedPresentation

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

object NpcPresentations {
    @JvmStatic
    fun prepare(parent: NpcHandle, options: NpcPresentationOptions): NpcPresentationStart =
        (parent as? NpcPresentationParent)?.preparePresentation(options)
            ?: rejectedPresentation(
                "NPC_PRESENTATION_UNSUPPORTED",
                "Parent does not support personal presentations",
            )
}
