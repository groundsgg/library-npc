package gg.grounds.npc.minestom

import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.CachedPacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.property.ServerProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NpcSharedMetadataTest {
    private fun display(fixture: RendererFixture, vararg players: TestPlayer) =
        NpcItemDisplay(Any()).also { display ->
            display.setAutoViewable(false)
            display.setInstance(fixture.instance).join()
            players.forEach { display.addViewer(it) }
            players.forEach {
                it.connection.packets.clear()
                it.connection.envelopes.clear()
                it.connection.captureEnvelopes = true
            }
        }

    @Test
    fun `default viewers share a serialized frame while personalized pose stays independent`() {
        RendererFixture().use { fixture ->
            val first = fixture.player()
            val personal = fixture.player()
            val second = fixture.player()
            val display = display(fixture, first, personal, second)
            display.visualState = { if (it === personal) 2.0 to true else DEFAULT_NPC_VISUAL }
            display.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = Vec(2.0, 3.0, 4.0)
                it.scale = Vec(1.0, 2.0, 3.0)
                it.setOnFire(true)
            }

            val common =
                assertInstanceOf(CachedPacket::class.java, first.connection.envelopes.single())
            assertSame(common, second.connection.envelopes.single())
            assertSame(
                common.body(ConnectionState.PLAY),
                (second.connection.envelopes.single() as CachedPacket).body(ConnectionState.PLAY),
            )
            assertInstanceOf(
                EntityMetaDataPacket::class.java,
                personal.connection.envelopes.single(),
            )

            val sharedPose = first.connection.packets.single() as EntityMetaDataPacket
            val personalPose = personal.connection.packets.single() as EntityMetaDataPacket
            val translation = MetadataDef.Display.TRANSLATION.index()
            val scale = MetadataDef.Display.SCALE.index()
            assertEquals(Vec(2.0, 3.0, 4.0), sharedPose.entries()[translation]!!.value())
            assertEquals(Vec(4.0, 6.0, 8.0), personalPose.entries()[translation]!!.value())
            assertEquals(Vec(1.0, 2.0, 3.0), sharedPose.entries()[scale]!!.value())
            assertEquals(Vec(2.0, 4.0, 6.0), personalPose.entries()[scale]!!.value())
            assertEquals(1.toByte(), sharedPose.entries()[0]!!.value())
            assertEquals(0x41.toByte(), personalPose.entries()[0]!!.value())
        }
    }

    @Test
    fun `a single common recipient avoids a serialized cache even beside a personal viewer`() {
        for (withPersonalViewer in listOf(false, true)) {
            RendererFixture().use { fixture ->
                val common = fixture.player()
                val personal = if (withPersonalViewer) fixture.player() else null
                val players = listOfNotNull(common, personal)
                val display = display(fixture, *players.toTypedArray())
                display.visualState = { if (it === personal) 2.0 to false else DEFAULT_NPC_VISUAL }
                display.editEntityMeta(ItemDisplayMeta::class.java) {
                    it.translation = Vec(2.0, 3.0, 4.0)
                }
                val packet =
                    assertInstanceOf(
                        EntityMetaDataPacket::class.java,
                        common.connection.envelopes.single(),
                    )
                assertEquals(
                    Vec(2.0, 3.0, 4.0),
                    packet.entries()[MetadataDef.Display.TRANSLATION.index()]!!.value(),
                )
                if (personal != null) {
                    val own =
                        assertInstanceOf(
                            EntityMetaDataPacket::class.java,
                            personal.connection.envelopes.single(),
                        )
                    assertEquals(
                        Vec(4.0, 6.0, 8.0),
                        own.entries()[MetadataDef.Display.TRANSLATION.index()]!!.value(),
                    )
                }
            }
        }
    }

    @Test
    fun `queued old frames retain their pose when a new frame is rendered before encoding`() {
        RendererFixture().use { fixture ->
            val first = fixture.player()
            val second = fixture.player()
            val display = display(fixture, first, second)
            first.connection.decodePackets = false
            second.connection.decodePackets = false
            display.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = Vec(2.0, 3.0, 4.0)
            }
            display.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = Vec(9.0, 8.0, 7.0)
            }
            val old = assertInstanceOf(CachedPacket::class.java, first.connection.envelopes[0])
            val next = assertInstanceOf(CachedPacket::class.java, first.connection.envelopes[1])
            assertNotSame(old, next)
            assertSame(old, second.connection.envelopes[0])
            val translation = MetadataDef.Display.TRANSLATION.index()
            val oldPose =
                SendablePacket.extractServerPacket(ConnectionState.PLAY, old)
                    as EntityMetaDataPacket
            val newPose =
                SendablePacket.extractServerPacket(ConnectionState.PLAY, next)
                    as EntityMetaDataPacket
            assertEquals(Vec(2.0, 3.0, 4.0), oldPose.entries()[translation]!!.value())
            assertEquals(Vec(9.0, 8.0, 7.0), newPose.entries()[translation]!!.value())
            assertNotSame(old.body(ConnectionState.PLAY), next.body(ConnectionState.PLAY))
        }
    }

    @Test
    fun `translatable metadata preserves the configured translation policy`() {
        RendererFixture().use { fixture ->
            val first = fixture.player()
            val second = fixture.player()
            val display = display(fixture, first, second)
            display.customName = Component.translatable("npc.test.name")
            val expected =
                if (ServerProperties.AUTOMATIC_COMPONENT_TRANSLATION.get())
                    EntityMetaDataPacket::class.java
                else CachedPacket::class.java
            assertInstanceOf(expected, first.connection.envelopes.single())
            assertInstanceOf(expected, second.connection.envelopes.single())
            val packet = first.connection.packets.single() as EntityMetaDataPacket
            assertTrue(packet.components().contains(Component.translatable("npc.test.name")))
        }
    }

    @Test
    fun `personal only audience receives its scaled pose without a common frame`() {
        RendererFixture().use { fixture ->
            val personal = fixture.player()
            val display = display(fixture, personal)
            display.visualState = { 3.0 to false }
            display.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = Vec(2.0, 3.0, 4.0)
            }
            val packet =
                assertInstanceOf(
                    EntityMetaDataPacket::class.java,
                    personal.connection.envelopes.single(),
                )
            assertEquals(
                Vec(6.0, 9.0, 12.0),
                packet.entries()[MetadataDef.Display.TRANSLATION.index()]!!.value(),
            )
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            MinecraftServer.init()
        }
    }
}
