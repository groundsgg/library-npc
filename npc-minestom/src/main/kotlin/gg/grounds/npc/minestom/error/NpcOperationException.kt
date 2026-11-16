package gg.grounds.npc.minestom.error

/**
 * Failure of queued NPC runtime work. [code] is stable; the original runtime failure remains
 * [cause].
 */
class NpcOperationException
@JvmOverloads
constructor(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
