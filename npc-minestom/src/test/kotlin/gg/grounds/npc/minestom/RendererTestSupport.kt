package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection

internal fun sceneTransform(position: NpcVector3 = ZERO) =
    NpcRenderTransform(NpcTransform(position))

internal class RendererFixture(
    val instance: InstanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()
) : AutoCloseable {
    fun player(position: Pos = Pos.ZERO, uuid: UUID = UUID.randomUUID()) =
        TestPlayer(uuid).also { it.setInstance(instance, position).join() }

    override fun close() {
        instance.entities.toList().forEach { it.remove() }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }
}

internal class TestPlayer(uuid: UUID = UUID.randomUUID()) :
    Player(CapturingConnection(), GameProfile(uuid, "test")) {
    val connection
        get() = playerConnection as CapturingConnection
}

internal class CapturingConnection : PlayerConnection() {
    val packets = CopyOnWriteArrayList<ServerPacket>()
    val envelopes = CopyOnWriteArrayList<SendablePacket>()
    var captureEnvelopes = false
    var decodePackets = true
    @Volatile var beforeSend: ((ServerPacket) -> Unit)? = null

    override fun sendPacket(packet: SendablePacket) {
        if (captureEnvelopes) envelopes += packet
        if (!decodePackets) return
        val decoded = SendablePacket.extractServerPacket(ConnectionState.PLAY, packet) ?: return
        beforeSend?.invoke(decoded)
        packets += decoded
    }

    override fun getRemoteAddress() = InetSocketAddress(0)

    fun flags(id: Int) =
        packets
            .filterIsInstance<EntityMetaDataPacket>()
            .filter { it.entityId() == id }
            .mapNotNull { it.entries()[0]?.value() as? Byte }
            .map { it.toInt() }

    fun spawns(id: Int) =
        packets.filterIsInstance<SpawnEntityPacket>().filter { it.entityId() == id }
}
