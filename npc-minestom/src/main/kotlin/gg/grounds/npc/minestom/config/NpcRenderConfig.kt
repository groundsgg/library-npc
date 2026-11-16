package gg.grounds.npc.minestom.config

/** Immutable validated options. Create with [builder]; reuse through [toBuilder]. */
class NpcRenderConfig private constructor(builder: Builder) {
    val viewDistance: Double = builder.viewDistance
    val retentionDistance: Double = builder.retentionDistance
    val verticalDistance: Double = builder.verticalDistance
    val nearDistance: Double = builder.nearDistance
    val nearRetention: Double = builder.nearRetention
    val midDistance: Double = builder.midDistance
    val midRetention: Double = builder.midRetention
    val nearIntervalMillis: Long = builder.nearIntervalMillis
    val midIntervalMillis: Long = builder.midIntervalMillis
    val farIntervalMillis: Long = builder.farIntervalMillis
    val idleClip: String? = builder.idleClip
    val variationClip: String? = builder.variationClip
    val variationDelayMillis: Long = builder.variationDelayMillis
    val variationMinDistance: Double = builder.variationMinDistance

    init {
        listOf(
                "viewDistance" to viewDistance,
                "retentionDistance" to retentionDistance,
                "verticalDistance" to verticalDistance,
                "nearDistance" to nearDistance,
                "nearRetention" to nearRetention,
                "midDistance" to midDistance,
                "midRetention" to midRetention,
                "variationMinDistance" to variationMinDistance,
            )
            .forEach { (name, value) ->
                require(value.isFinite() && value > 0) { "$name must be finite and positive" }
            }
        require(retentionDistance >= viewDistance) { "retentionDistance must be >= viewDistance" }
        require(
            nearDistance <= nearRetention &&
                nearRetention <= midDistance &&
                midDistance <= midRetention &&
                midRetention <= viewDistance
        ) {
            "Distances must satisfy nearDistance <= nearRetention <= midDistance <= midRetention <= viewDistance"
        }
        listOf(
                "nearIntervalMillis" to nearIntervalMillis,
                "midIntervalMillis" to midIntervalMillis,
                "farIntervalMillis" to farIntervalMillis,
            )
            .forEach { (name, value) ->
                require(value in 50..10_000 && value % 50 == 0L) {
                    "$name must be 50..10000 milliseconds in multiples of 50"
                }
            }
        require(nearIntervalMillis <= midIntervalMillis && midIntervalMillis <= farIntervalMillis) {
            "Intervals must satisfy nearIntervalMillis <= midIntervalMillis <= farIntervalMillis"
        }
        require(variationDelayMillis in 50..3_600_000) {
            "variationDelayMillis must be 50..3600000"
        }
    }

    fun toBuilder(): Builder =
        builder()
            .viewDistance(viewDistance)
            .retentionDistance(retentionDistance)
            .verticalDistance(verticalDistance)
            .nearDistance(nearDistance)
            .nearRetention(nearRetention)
            .midDistance(midDistance)
            .midRetention(midRetention)
            .nearIntervalMillis(nearIntervalMillis)
            .midIntervalMillis(midIntervalMillis)
            .farIntervalMillis(farIntervalMillis)
            .idleClip(idleClip)
            .variationClip(variationClip)
            .variationDelayMillis(variationDelayMillis)
            .variationMinDistance(variationMinDistance)

    companion object {
        @JvmStatic fun builder(): Builder = Builder()
    }

    /**
     * Mutable construction helper; confine a builder to one thread. Built options are immutable.
     */
    class Builder internal constructor() {
        internal var viewDistance: Double = 64.0
        internal var retentionDistance: Double = 72.0
        internal var verticalDistance: Double = 32.0
        internal var nearDistance: Double = 16.0
        internal var nearRetention: Double = 18.0
        internal var midDistance: Double = 32.0
        internal var midRetention: Double = 36.0
        internal var nearIntervalMillis: Long = 200
        internal var midIntervalMillis: Long = 250
        internal var farIntervalMillis: Long = 500
        internal var idleClip: String? = null
        internal var variationClip: String? = null
        internal var variationDelayMillis: Long = 20_000
        internal var variationMinDistance: Double = 5.0

        fun viewDistance(value: Double): Builder = apply { viewDistance = value }

        fun retentionDistance(value: Double): Builder = apply { retentionDistance = value }

        fun verticalDistance(value: Double): Builder = apply { verticalDistance = value }

        fun nearDistance(value: Double): Builder = apply { nearDistance = value }

        fun nearRetention(value: Double): Builder = apply { nearRetention = value }

        fun midDistance(value: Double): Builder = apply { midDistance = value }

        fun midRetention(value: Double): Builder = apply { midRetention = value }

        fun nearIntervalMillis(value: Long): Builder = apply { nearIntervalMillis = value }

        fun midIntervalMillis(value: Long): Builder = apply { midIntervalMillis = value }

        fun farIntervalMillis(value: Long): Builder = apply { farIntervalMillis = value }

        fun idleClip(value: String?): Builder = apply { idleClip = value }

        fun variationClip(value: String?): Builder = apply { variationClip = value }

        fun variationDelayMillis(value: Long): Builder = apply { variationDelayMillis = value }

        fun variationMinDistance(value: Double): Builder = apply { variationMinDistance = value }

        fun build(): NpcRenderConfig = NpcRenderConfig(this)
    }
}
