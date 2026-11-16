package gg.grounds.npc.minestom.internal.presentation

import gg.grounds.npc.minestom.presentation.NpcPresentationResult
import gg.grounds.npc.minestom.presentation.NpcPresentationStart

import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.validation.NpcProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

internal fun rejectedPresentation(code: String, message: String): NpcPresentationStart =
    object : NpcPresentationStart {
        private val result =
            CompletableFuture.completedFuture<NpcPresentationResult>(
                NpcPresentationResult.Rejected(listOf(NpcProblem(code, "$", message)))
            )

        override fun ready(): CompletionStage<NpcPresentationResult> =
            result.minimalCompletionStage()

        override fun cancel(): CompletionStage<Void> = CompletableFuture.completedFuture(null)
    }
