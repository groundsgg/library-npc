package gg.grounds.npc.minestom

import java.util.concurrent.CompletionStage

interface NpcStart {
    fun ready(): CompletionStage<NpcPrepareResult>

    fun cancel(): CompletionStage<Void>
}
