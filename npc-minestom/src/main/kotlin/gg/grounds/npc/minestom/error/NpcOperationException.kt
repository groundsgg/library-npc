package gg.grounds.npc.minestom.error

/**
 * Failure of queued NPC runtime work. The stable code identifies the operation category, and the
 * original runtime failure is retained as the cause.
 *
 * @property code Stable machine-readable category; hosts may define integration-specific codes.
 */
public class NpcOperationException
@JvmOverloads
constructor(
    /** Stable machine-readable category; hosts may define integration-specific codes. */
    public val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
