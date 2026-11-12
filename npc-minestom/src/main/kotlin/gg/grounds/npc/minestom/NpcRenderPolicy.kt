// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import kotlin.math.abs
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

/** Shared admission rule for initial attachment and subsequent viewer movement. */
internal class NpcRenderPolicy(private val config: NpcRenderConfig) {
    val VIEW_DISTANCE_SQUARED = config.viewDistance * config.viewDistance
    private val RETENTION_DISTANCE_SQUARED = config.retentionDistance * config.retentionDistance
    private val VERTICAL_DISTANCE = config.verticalDistance

    fun canView(
        player: Player,
        instance: Instance,
        anchor: NpcVector3,
        wasVisible: Boolean = false,
    ): Boolean =
        distanceSquared(player, instance, anchor) <=
            (if (wasVisible) RETENTION_DISTANCE_SQUARED else VIEW_DISTANCE_SQUARED) &&
            abs(player.position.y() - anchor.y) <= VERTICAL_DISTANCE

    fun updateIntervalMillis(distanceSquared: Double): Long =
        when {
            distanceSquared <= config.nearDistance * config.nearDistance -> 200L
            distanceSquared <= config.midDistance * config.midDistance -> 250L
            else -> 500L
        }

    fun viewerRate(distanceSquared: Double, previous: NpcViewerRate?): NpcViewerRate =
        when {
            previous == NpcViewerRate.NEAR &&
                distanceSquared <= config.nearRetention * config.nearRetention -> NpcViewerRate.NEAR
            distanceSquared <= config.nearDistance * config.nearDistance -> NpcViewerRate.NEAR
            previous == NpcViewerRate.MID &&
                distanceSquared <= config.midRetention * config.midRetention -> NpcViewerRate.MID
            distanceSquared <= config.midDistance * config.midDistance -> NpcViewerRate.MID
            else -> NpcViewerRate.FAR
        }

    fun distanceSquared(player: Player, instance: Instance, anchor: NpcVector3): Double {
        if (player.instance !== instance) return Double.POSITIVE_INFINITY
        val p = player.position
        val dx = p.x() - anchor.x
        val dy = p.y() - anchor.y
        val dz = p.z() - anchor.z
        return dx * dx + dy * dy + dz * dz
    }
}
