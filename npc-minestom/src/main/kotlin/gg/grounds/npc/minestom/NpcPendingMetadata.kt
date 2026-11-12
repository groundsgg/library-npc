// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.MetadataDef

/** One bounded accumulator per active distance cohort, never per player or animation frame. */
internal class NpcPendingMetadata {
    private val pending = mutableMapOf<NpcViewerRate, Delta>()
    private var single: Delta? = null

    val canSendDirect: Boolean
        get() = single?.isEmpty == true

    fun configure(intervals: Set<NpcViewerRate>) {
        pending.keys.retainAll(intervals)
        intervals.forEach { pending.getOrPut(it) { Delta() } }
        single = pending.values.singleOrNull()
    }

    fun merge(changes: Map<Int, Metadata.Entry<*>>) {
        if (changes.isEmpty()) return
        pending.values.forEach { it.merge(changes) }
    }

    fun drain(interval: NpcViewerRate): Map<Int, Metadata.Entry<*>>? {
        return pending[interval]?.drain()
    }

    fun clear() {
        pending.clear()
        single = null
    }

    private class Delta {
        // MetadataHolder publishes a detached map. Keep that immutable first delta; copy
        // only when another frame must be merged into it for a slower receiving cohort.
        private var first: Map<Int, Metadata.Entry<*>>? = null
        private var merged: MutableMap<Int, Metadata.Entry<*>>? = null

        val isEmpty: Boolean
            get() = first == null && merged == null

        fun merge(changes: Map<Int, Metadata.Entry<*>>) {
            val accumulating = merged
            if (accumulating != null) {
                accumulating.putAll(changes)
            } else {
                val previous = first
                if (previous == null) first = changes
                else {
                    merged = previous.toMutableMap().also { it.putAll(changes) }
                    first = null
                }
            }
        }

        fun drain(): Map<Int, Metadata.Entry<*>>? {
            val owned = merged
            val result = owned ?: first ?: return null
            // Transfer ownership: subsequent frames cannot mutate a packet already queued.
            first = null
            merged = null
            val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
            val delay = MetadataDef.Display.INTERPOLATION_DELAY.index()
            if (duration !in result && delay in result) return result
            val adjusted = owned ?: result.toMutableMap()
            // Every viewer gets its cohort's fixed duration in the entry/transition snapshot.
            adjusted.remove(duration)
            adjusted[delay] = Metadata.VarInt(0)
            return adjusted
        }
    }
}
