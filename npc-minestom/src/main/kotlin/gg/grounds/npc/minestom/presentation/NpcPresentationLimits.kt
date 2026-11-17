package gg.grounds.npc.minestom.presentation

/**
 * Parent-owned limits for simultaneously pending/active personal groups and their additional
 * display parts. Both budgets must be positive.
 *
 * @property maxPresentations Positive per-parent private-group count budget; default 32.
 * @property maxAdditionalParts Positive per-parent extra-display budget; default 1024.
 */
public data class NpcPresentationLimits
@JvmOverloads
constructor(
    /** Positive per-parent private-group count budget; default 32. */
    val maxPresentations: Int = 32,
    /** Positive per-parent extra-display budget; default 1024. */
    val maxAdditionalParts: Int = 1024,
) {
    init {
        require(maxPresentations > 0 && maxAdditionalParts > 0)
    }
}
