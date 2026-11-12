package consumer

import gg.grounds.npc.core.*
import gg.grounds.npc.minestom.*
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

fun gesture(parent: NpcHandle, player: Player): CompletionStage<Void> {
    val start = NpcPresentations.prepare(parent, NpcPresentationOptions(player))
    return start.ready().thenCompose { result ->
        when (result) {
            is NpcPresentationResult.Ready ->
                result.handle
                    .publish()
                    .thenCompose { result.handle.applyHeadLook(20.0, -10.0) }
                    .handle { _, failure ->
                        start.cancel().thenCompose {
                            if (failure == null)
                                java.util.concurrent.CompletableFuture.completedFuture<Void>(null)
                            else java.util.concurrent.CompletableFuture.failedFuture<Void>(failure)
                        }
                    }
                    .thenCompose { it }
            is NpcPresentationResult.Rejected -> start.cancel()
        }
    }
}

fun main() {
    val model =
        NpcModel(
            "example:kotlin",
            NpcBounds(NpcVector3.ZERO, NpcVector3.ONE),
            emptyList(),
            listOf(
                NpcPart(null, "example:cube", NpcVector3.ZERO, NpcVector3.ZERO, NpcVector3.ZERO)
            ),
            emptyMap(),
            emptyMap(),
        )
    check(NpcPoseSampler.sample(model, null, 0.0).parts.size == 1)
    println("Kotlin consumer OK: ${model.id}")
}
