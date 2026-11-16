package gg.grounds.npc.minestom.presentation

data class NpcPresentationLimits
@JvmOverloads
constructor(
    val maxPresentations: Int = 32,
    val maxAdditionalParts: Int = 1024,
) {
    init {
        require(maxPresentations > 0 && maxAdditionalParts > 0)
    }
}
