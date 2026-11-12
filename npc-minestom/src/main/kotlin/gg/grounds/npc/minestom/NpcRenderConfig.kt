package gg.grounds.npc.minestom

data class NpcRenderConfig
@JvmOverloads
constructor(
    val viewDistance: Double = 64.0,
    val retentionDistance: Double = 72.0,
    val verticalDistance: Double = 32.0,
    val nearDistance: Double = 16.0,
    val nearRetention: Double = 18.0,
    val midDistance: Double = 32.0,
    val midRetention: Double = 36.0,
    val nearIntervalMillis: Long = 200,
    val midIntervalMillis: Long = 250,
    val farIntervalMillis: Long = 500,
    val idleClip: String? = null,
    val variationClip: String? = null,
    val variationDelayMillis: Long = 20_000,
    val variationMinDistance: Double = 5.0,
) {
    init {
        require(
            listOf(
                    viewDistance,
                    retentionDistance,
                    verticalDistance,
                    nearDistance,
                    nearRetention,
                    midDistance,
                    midRetention,
                    variationMinDistance,
                )
                .all { it.isFinite() && it > 0 }
        )
        require(
            retentionDistance >= viewDistance &&
                nearDistance <= nearRetention &&
                nearRetention <= midDistance &&
                midDistance <= midRetention &&
                midRetention <= viewDistance
        )
        require(
            listOf(nearIntervalMillis, midIntervalMillis, farIntervalMillis).all {
                it in 50..10_000 && it % 50 == 0L
            }
        )
        require(nearIntervalMillis <= midIntervalMillis && midIntervalMillis <= farIntervalMillis)
        require(variationDelayMillis in 50..3_600_000)
    }
}
