package gg.grounds.npc.minestom.internal.movement

import gg.grounds.npc.minestom.internal.rendering.NpcItemDisplay
import gg.grounds.npc.minestom.transform.NpcRenderTransform

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/** Owns relocation I/O and scheduler continuations under the parent's ownership monitor. */
internal class NpcMovementController(
    private val lock: Any,
    private val displays: List<NpcItemDisplay>,
    private val currentTransform: () -> NpcRenderTransform,
    private val commitTransform: (NpcRenderTransform, Boolean) -> Unit,
    private val afterUpdate: () -> Unit,
) {
    private var closed = false
    private var revision = 0L
    private val movements = HashSet<java.util.concurrent.CompletableFuture<Void>>()
    private val movementTasks = HashSet<Task>()
    private val externalMovements = HashSet<java.util.concurrent.CompletableFuture<Void>>()
    private val movementFailures = ArrayList<Throwable>()
    private fun movementDrain(): java.util.concurrent.CompletionStage<Void> {
        val (pending, failure) =
            synchronized(lock) { movements.toList() to movementFailures.firstOrNull() }
        return java.util.concurrent.CompletableFuture.allOf(*pending.toTypedArray()).thenCompose {
            if (failure == null) java.util.concurrent.CompletableFuture.completedFuture<Void>(null)
            else java.util.concurrent.CompletableFuture.failedFuture<Void>(failure)
        }
    }

    fun applyTransform(
        transform: NpcRenderTransform
    ): java.util.concurrent.CompletionStage<Void> {
        val result = java.util.concurrent.CompletableFuture<Void>()
        val token = update {
            if (closed)
                return java.util.concurrent.CompletableFuture.failedFuture(
                    IllegalStateException("NPC closed")
                )
            val revision = ++this.revision
            if (transform.root.position == currentTransform().root.position) {
                commitTransform(transform, false)
                return java.util.concurrent.CompletableFuture.completedFuture(null)
            }
            movements += result
            externalMovements += result
            revision
        }
        result.whenComplete { _, failure ->
            synchronized(lock) {
                movements.remove(result)
                externalMovements.remove(result)
                if (failure != null) movementFailures += failure
            }
        }
        val anchor = transform.root.position
        if (synchronized(lock) { closed || token != revision }) {
            result.complete(null)
            return result.minimalCompletionStage()
        }
        try {
            displays
                .first()
                .instance
                .loadOptionalChunk(Pos(anchor.x, anchor.y, anchor.z))
                .whenComplete { chunk, error ->
                    if (error != null) result.completeExceptionally(error)
                    else if (chunk == null)
                        result.completeExceptionally(
                            IllegalStateException("NPC destination chunk unavailable")
                        )
                    else
                        queueMovement(
                            result,
                            token,
                            {
                                val moved =
                                    ArrayList<java.util.concurrent.CompletableFuture<Void>>()
                                for (display in displays) {
                                    if (synchronized(lock) { closed || token != revision }) break
                                    try {
                                        moved += display.teleport(Pos(anchor.x, anchor.y, anchor.z))
                                    } catch (problem: Throwable) {
                                        moved += CompletableFuture.failedFuture<Void>(problem)
                                        break
                                    }
                                }
                                java.util.concurrent.CompletableFuture.allOf(*moved.toTypedArray())
                            },
                            foreign = true,
                        ) {
                            queueMovement(
                                result,
                                token,
                                {
                                    commitTransform(transform, true)
                                    java.util.concurrent.CompletableFuture.completedFuture(null)
                                },
                            ) {
                                result.complete(null)
                            }
                        }
                }
        } catch (error: Throwable) {
            result.completeExceptionally(error)
        }
        return result.minimalCompletionStage()
    }

    /**
     * Every continuation that can touch entities or their metadata returns to the instance owner.
     */
    private fun queueMovement(
        result: java.util.concurrent.CompletableFuture<Void>,
        token: Long,
        action: () -> java.util.concurrent.CompletionStage<Void>,
        foreign: Boolean = false,
        after: () -> Unit,
    ) {
        var skipped = false
        var failure: Throwable? = null
        synchronized(lock) {
            // Atomically hand completed I/O ownership to a cancellable owner continuation.
            externalMovements.remove(result)
            if (closed || token != revision || result.isDone) skipped = true
            else
                try {
                    val holder = arrayOfNulls<Task>(1)
                    val task =
                        displays
                            .first()
                            .instance
                            .scheduler()
                            .buildTask {
                                val accepted =
                                    synchronized(lock) {
                                        movementTasks.remove(holder[0])
                                        if (closed || token != revision || result.isDone) false
                                        else {
                                            externalMovements += result
                                            true
                                        }
                                    }
                                if (!accepted) result.complete(null)
                                else
                                    try {
                                        // Teleports invoke host event listeners: reserve under
                                        // lock, execute outside.
                                        val stage =
                                            if (foreign) action()
                                            else
                                                update {
                                                    if (
                                                        closed || token != revision || result.isDone
                                                    )
                                                        null
                                                    else action()
                                                }
                                        if (stage == null) result.complete(null)
                                        else
                                            stage.whenComplete { _, error ->
                                                if (error != null)
                                                    result.completeExceptionally(error)
                                                else after()
                                            }
                                    } catch (error: Throwable) {
                                        result.completeExceptionally(error)
                                    }
                            }
                            .delay(TaskSchedule.tick(1))
                            .schedule()
                    holder[0] = task
                    movementTasks += task
                } catch (error: Throwable) {
                    failure = error
                }
        }
        if (skipped) result.complete(null)
        if (failure != null) result.completeExceptionally(failure!!)
    }


    fun drain(): java.util.concurrent.CompletionStage<Void> = movementDrain()

    fun close(): java.util.concurrent.CompletionStage<Void> {
        val pending = synchronized(lock) {
            closed = true
            revision++
            movementTasks.forEach(Task::cancel)
            movementTasks.clear()
            movements.filter { it !in externalMovements }
        }
        pending.forEach { it.complete(null) }
        return movementDrain()
    }

    private inline fun <T> update(action: () -> T): T {
        try { return synchronized(lock) { action() } } finally { afterUpdate() }
    }
}
