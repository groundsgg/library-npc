package gg.grounds.npc.core

import java.util.Collections

@ConsistentCopyVisibility
data class NpcVector3
private constructor(
    val x: Double,
    val y: Double,
    val z: Double,
    @Suppress("unused") private val canonical: Unit,
) {
    constructor(
        x: Double,
        y: Double,
        z: Double,
    ) : this(canonicalComponent(x), canonicalComponent(y), canonicalComponent(z), Unit)

    companion object {
        @JvmField val ZERO = NpcVector3(0.0, 0.0, 0.0)
        @JvmField val ONE = NpcVector3(1.0, 1.0, 1.0)
    }
}

data class NpcBounds(val center: NpcVector3, val size: NpcVector3) {
    init {
        require(size.x > 0 && size.y > 0 && size.z > 0) { "Bounds must be positive" }
    }
}

data class NpcTransform
@JvmOverloads
constructor(
    val position: NpcVector3,
    val rotationDegrees: NpcVector3 = NpcVector3.ZERO,
    val scale: NpcVector3 = NpcVector3.ONE,
)

internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

internal fun resourceId(value: String): String = value.also {
    require(it.length <= 256 && it.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) {
        "Invalid resource ID"
    }
}

internal fun localId(value: String): String = value.also {
    require(it.length in 1..128 && it.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid local ID" }
}

private fun canonicalComponent(value: Double): Double {
    require(value.isFinite()) { "Non-finite vector" }
    return if (value == 0.0) 0.0 else value
}
