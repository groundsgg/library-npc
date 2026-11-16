package gg.grounds.npc.minestom.internal.lifecycle

import gg.grounds.npc.minestom.error.NpcOperationException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

internal fun operationFailure(error: Throwable): Throwable {
    val cause = if (error is CompletionException) error.cause ?: error else error
    return when (cause) {
        is IllegalArgumentException, is CancellationException, is NpcOperationException -> cause
        else -> NpcOperationException("NPC_OPERATION_FAILED", cause.message ?: "NPC operation failed", cause)
    }
}
