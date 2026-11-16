package gg.grounds.npc.minestom.config

/**
 * Immutable visibility, update-cohort and idle-variation policy. Distances are world blocks and
 * times are milliseconds. Builder validation enforces ordered hysteresis distances and tick-aligned
 * intervals.
 *
 * @property viewDistance Admission radius in world blocks; default 64.
 * @property retentionDistance Retention radius in blocks, at least viewDistance; default 72.
 * @property verticalDistance Positive vertical visibility bound in world blocks; default 32.
 * @property nearDistance Near-cohort admission radius in blocks; default 16.
 * @property nearRetention Near-cohort hysteresis radius in blocks, between nearDistance and
 *   midDistance; default 18.
 * @property midDistance Mid-cohort admission radius in blocks; default 32.
 * @property midRetention Mid-cohort hysteresis radius in blocks, between midDistance and
 *   viewDistance; default 36.
 * @property nearIntervalMillis Near-cohort update interval, 50..10000 ms in 50 ms steps;
 *   default 200.
 * @property midIntervalMillis Mid-cohort interval, at least nearIntervalMillis; default 250 ms.
 * @property farIntervalMillis Far-cohort interval, at least midIntervalMillis; default 500 ms.
 * @property idleClip Existing clip ID for idle playback, or null for rest. Existence is checked
 *   when a model is available.
 * @property variationClip Optional shared idle-variation clip ID; null disables variation.
 * @property variationDelayMillis Shared variation delay in 50..3600000 milliseconds; default 20000.
 * @property variationMinDistance Positive world-block distance within which shared idle variation
 *   is suppressed; default 5.
 */
public class NpcRenderConfig private constructor(builder: Builder) {
    /** Admission radius in world blocks; default 64. */
    public val viewDistance: Double = builder.viewDistance
    /** Retention radius in blocks, at least viewDistance; default 72. */
    public val retentionDistance: Double = builder.retentionDistance
    /** Positive vertical visibility bound in world blocks; default 32. */
    public val verticalDistance: Double = builder.verticalDistance
    /** Near-cohort admission radius in blocks; default 16. */
    public val nearDistance: Double = builder.nearDistance
    /**
     * Near-cohort hysteresis radius in blocks, between nearDistance and midDistance; default 18.
     */
    public val nearRetention: Double = builder.nearRetention
    /** Mid-cohort admission radius in blocks; default 32. */
    public val midDistance: Double = builder.midDistance
    /** Mid-cohort hysteresis radius in blocks, between midDistance and viewDistance; default 36. */
    public val midRetention: Double = builder.midRetention
    /** Near-cohort update interval, 50..10000 ms in 50 ms steps; default 200. */
    public val nearIntervalMillis: Long = builder.nearIntervalMillis
    /** Mid-cohort interval, at least nearIntervalMillis; default 250 ms. */
    public val midIntervalMillis: Long = builder.midIntervalMillis
    /** Far-cohort interval, at least midIntervalMillis; default 500 ms. */
    public val farIntervalMillis: Long = builder.farIntervalMillis
    /**
     * Existing clip ID for idle playback, or null for rest. Existence is checked when a model is
     * available.
     */
    public val idleClip: String? = builder.idleClip
    /** Optional shared idle-variation clip ID; null disables variation. */
    public val variationClip: String? = builder.variationClip
    /** Shared variation delay in 50..3600000 milliseconds; default 20000. */
    public val variationDelayMillis: Long = builder.variationDelayMillis
    /**
     * Positive world-block distance within which shared idle variation is suppressed; default 5.
     */
    public val variationMinDistance: Double = builder.variationMinDistance

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

    /**
     * Copy these immutable options into an independent mutable builder. Later changes cannot mutate
     * this instance.
     */
    public fun toBuilder(): Builder =
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

    /**
     * Shared factories and constants; Java entrypoints are exposed with their documented static
     * annotations.
     */
    public companion object {
        /** Create a thread-confined builder; defaults are validated when build is called. */
        @JvmStatic public fun builder(): Builder = Builder()
    }

    /**
     * Mutable construction helper; confine a builder to one thread. Built options are immutable.
     */
    public class Builder internal constructor() {
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

        /**
         * Configure Admission radius in world blocks; default 64. Validation occurs at build;
         * returns this builder for chaining.
         */
        public fun viewDistance(value: Double): Builder = apply { viewDistance = value }

        /**
         * Configure Retention radius in blocks, at least viewDistance; default 72. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun retentionDistance(value: Double): Builder = apply { retentionDistance = value }

        /**
         * Configure Positive vertical visibility bound in world blocks; default 32. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun verticalDistance(value: Double): Builder = apply { verticalDistance = value }

        /**
         * Configure Near-cohort admission radius in blocks; default 16. Validation occurs at build;
         * returns this builder for chaining.
         */
        public fun nearDistance(value: Double): Builder = apply { nearDistance = value }

        /**
         * Configure Near-cohort hysteresis radius in blocks, between nearDistance and midDistance;
         * default 18. Validation occurs at build; returns this builder for chaining.
         */
        public fun nearRetention(value: Double): Builder = apply { nearRetention = value }

        /**
         * Configure Mid-cohort admission radius in blocks; default 32. Validation occurs at build;
         * returns this builder for chaining.
         */
        public fun midDistance(value: Double): Builder = apply { midDistance = value }

        /**
         * Configure Mid-cohort hysteresis radius in blocks, between midDistance and viewDistance;
         * default 36. Validation occurs at build; returns this builder for chaining.
         */
        public fun midRetention(value: Double): Builder = apply { midRetention = value }

        /**
         * Configure Near-cohort update interval, 50..10000 ms in 50 ms steps; default 200.
         * Validation occurs at build; returns this builder for chaining.
         */
        public fun nearIntervalMillis(value: Long): Builder = apply { nearIntervalMillis = value }

        /**
         * Configure Mid-cohort interval, at least nearIntervalMillis; default 250 ms. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun midIntervalMillis(value: Long): Builder = apply { midIntervalMillis = value }

        /**
         * Configure Far-cohort interval, at least midIntervalMillis; default 500 ms. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun farIntervalMillis(value: Long): Builder = apply { farIntervalMillis = value }

        /**
         * Configure Existing clip ID for idle playback, or null for rest. Existence is checked when
         * a model is available. Validation occurs at build; returns this builder for chaining.
         */
        public fun idleClip(value: String?): Builder = apply { idleClip = value }

        /**
         * Configure Optional shared idle-variation clip ID; null disables variation. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun variationClip(value: String?): Builder = apply { variationClip = value }

        /**
         * Configure Shared variation delay in 50..3600000 milliseconds; default 20000. Validation
         * occurs at build; returns this builder for chaining.
         */
        public fun variationDelayMillis(value: Long): Builder = apply {
            variationDelayMillis = value
        }

        /**
         * Configure Positive world-block distance within which shared idle variation is suppressed;
         * default 5. Validation occurs at build; returns this builder for chaining.
         */
        public fun variationMinDistance(value: Double): Builder = apply {
            variationMinDistance = value
        }

        /**
         * Validate the configured values and create independent immutable options. Invalid ranges
         * or relationships throw IllegalArgumentException with the property name.
         */
        public fun build(): NpcRenderConfig = NpcRenderConfig(this)
    }
}
