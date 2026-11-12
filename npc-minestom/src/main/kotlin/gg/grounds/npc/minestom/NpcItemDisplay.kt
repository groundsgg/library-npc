// Extracted from Grounds minestom-lobby, commit 020a370ff414f85bb6ac495785605a8789ffbb19.
package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.MetadataHolder
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.utils.PacketSendingUtils

internal val DEFAULT_NPC_VISUAL = 1.0 to false

/** All pose updates pass through this per-viewer path, including animation and tracking. */
internal class NpcItemDisplay(private val lock: Any) : Entity(EntityType.ITEM_DISPLAY) {
    var viewerState: ((Player) -> Unit)? = null
    var visualState: ((Player) -> Pair<Double, Boolean>)? = null
    private var pendingUpdates: NpcPendingMetadata? = null
    var deferUpdates = false

    init {
        metadata = MetadataHolder { changes ->
            synchronized(lock) {
                if (!isRemoved) {
                    val pending = pendingUpdates
                    if (pending == null || (!deferUpdates && pending.canSendDirect))
                        sendMetadata(changes, viewers)
                    else pending.merge(changes)
                }
            }
        }
        entityMeta = ItemDisplayMeta(this, metadata)
        setNoGravity(true)
        setHasPhysics(false)
        // Explicit anchor moves still synchronize immediately; stationary visuals need no
        // heartbeat.
        setSynchronizationTicks(Long.MAX_VALUE / 2)
    }

    fun configureUpdates(rates: Set<NpcViewerRate>) {
        val pending = pendingUpdates ?: NpcPendingMetadata().also { pendingUpdates = it }
        pending.configure(rates)
    }

    fun flushUpdates(rate: NpcViewerRate, recipients: Collection<Player>) {
        pendingUpdates?.drain(rate)?.let { sendMetadata(it, recipients, true) }
    }

    fun clearUpdates() {
        pendingUpdates?.clear()
    }

    private fun sendMetadata(
        changes: Map<Int, Metadata.Entry<*>>,
        recipients: Collection<Player>,
        checkMembership: Boolean = false,
    ) {
        val common = EntityMetaDataPacket(entityId, changes)
        val commonViewers = ArrayList<Player>()
        recipients.forEach { player ->
            if (checkMembership && player !in viewers) return@forEach
            val entries = personalized(player, changes)
            if (entries === changes) commonViewers += player
            else player.sendPacket(EntityMetaDataPacket(entityId, entries))
        }
        if (commonViewers.size == 1) commonViewers[0].sendPacket(common)
        else if (commonViewers.size > 1) PacketSendingUtils.sendGroupedPacket(commonViewers, common)
    }

    private fun personalized(
        player: Player,
        changes: Map<Int, Metadata.Entry<*>>,
    ): Map<Int, Metadata.Entry<*>> {
        val state = visualState?.invoke(player) ?: DEFAULT_NPC_VISUAL
        val flags = (changes[0]?.value() as? Byte ?: 0).toInt()
        val desiredFlags = if (state.second) flags or 0x40 else flags and 0xBF
        val translation = MetadataDef.Display.TRANSLATION.index()
        val scale = MetadataDef.Display.SCALE.index()
        if (
            (0 !in changes || flags == desiredFlags) &&
                (state.first == 1.0 || (translation !in changes && scale !in changes))
        )
            return changes
        val entries = changes.toMutableMap()
        if (0 in changes) {
            entries[0] = Metadata.Byte(desiredFlags.toByte())
        }
        val meta = entityMeta as ItemDisplayMeta
        if (translation in changes)
            entries[translation] = Metadata.Vector3(meta.translation.mul(state.first))
        if (scale in changes) entries[scale] = Metadata.Vector3(meta.scale.mul(state.first))
        return entries
    }

    fun sendVisualFlags(player: Player) {
        val flags = Metadata.Byte(metadata.get(MetadataDef.ENTITY_FLAGS))
        player.sendPacket(EntityMetaDataPacket(entityId, personalized(player, mapOf(0 to flags))))
    }

    fun sendPoseState(player: Player, interpolationDuration: Int) {
        val meta = entityMeta as ItemDisplayMeta
        val flags = Metadata.Byte(metadata.get(MetadataDef.ENTITY_FLAGS))
        val fields =
            mutableMapOf<Int, Metadata.Entry<*>>(
                0 to flags,
                MetadataDef.Display.TRANSLATION.index() to Metadata.Vector3(meta.translation),
                MetadataDef.Display.SCALE.index() to Metadata.Vector3(meta.scale),
                MetadataDef.Display.ROTATION_LEFT.index() to Metadata.Quaternion(meta.leftRotation),
                MetadataDef.Display.ROTATION_RIGHT.index() to
                    Metadata.Quaternion(meta.rightRotation),
                MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index() to
                    Metadata.VarInt(interpolationDuration),
                MetadataDef.Display.INTERPOLATION_DELAY.index() to Metadata.VarInt(0),
            )
        player.sendPacket(EntityMetaDataPacket(entityId, personalized(player, fields)))
    }

    override fun updateNewViewer(player: Player) {
        synchronized(lock) {
            if (!isRemoved) {
                super.updateNewViewer(player)
                viewerState?.invoke(player)
            }
        }
    }
}
