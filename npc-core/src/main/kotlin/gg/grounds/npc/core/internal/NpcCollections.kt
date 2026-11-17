package gg.grounds.npc.core.internal

import java.util.Collections

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
