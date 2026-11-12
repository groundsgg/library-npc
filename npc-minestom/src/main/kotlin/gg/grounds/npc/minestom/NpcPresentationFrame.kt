package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcVector3
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

/** Actual sampler inputs, independent of metadata allocation and the host clock driver. */
internal data class NpcSampleFrame(
    val clipId: String?,
    val seconds: Double,
    val headLook: NpcVector3,
)

/** Private renderers enumerate their concrete connection, never the world's player set. */
internal sealed interface NpcRenderAudience {
    fun candidates(instance: Instance): Collection<Player>

    fun accepts(player: Player): Boolean

    data object Shared : NpcRenderAudience {
        override fun candidates(instance: Instance): Collection<Player> = instance.players

        override fun accepts(player: Player) = true
    }

    class Personal(private val player: Player, private val valid: () -> Boolean = { true }) :
        NpcRenderAudience {
        private val candidate = listOf(player)

        override fun candidates(instance: Instance): Collection<Player> = candidate

        override fun accepts(player: Player) =
            this.player === player && player.isOnline && !player.isRemoved && valid()
    }
}
