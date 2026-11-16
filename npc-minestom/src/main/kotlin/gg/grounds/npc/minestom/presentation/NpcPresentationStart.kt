package gg.grounds.npc.minestom.presentation

import java.util.concurrent.CompletionStage

interface NpcPresentationStart {
    fun ready(): CompletionStage<NpcPresentationResult>

    fun cancel(): CompletionStage<Void>
}
