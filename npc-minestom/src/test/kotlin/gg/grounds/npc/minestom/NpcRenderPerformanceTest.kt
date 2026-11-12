package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.event.entity.EntityTeleportEvent
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NpcRenderPerformanceTest {
    @Test
    fun `same uuid replacement receives a freshly computed distance cohort snapshot`() {
        RendererFixture().use { fixture ->
            val id = java.util.UUID.randomUUID()
            val first = fixture.player(Pos(15.0, 0.0, 0.0), id)
            val handle = create(fixture)
            first.remove()
            val replacement = fixture.player(Pos(17.0, 0.0, 0.0), id)
            val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
            for (display in fixture.instance.entities.filterIsInstance<NpcItemDisplay>()) {
                val values =
                    replacement.connection.packets
                        .filterIsInstance<EntityMetaDataPacket>()
                        .filter { it.entityId() == display.entityId }
                        .mapNotNull { it.entries()[duration]?.value() }
                assertEquals(
                    5,
                    values.last(),
                    "New connection must use middle cohort, without the old near retention band",
                )
            }
            handle.close()
        }
    }

    @Test
    fun `same uuid reconnect must enter admission range afresh before inheriting retention`() {
        RendererFixture().use { fixture ->
            val id = java.util.UUID.randomUUID()
            val first = fixture.player(Pos(50.0, 0.0, 0.0), id)
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            assertTrue(displays.all { first in it.viewers })
            first.remove()
            val replacement = fixture.player(Pos(68.0, 0.0, 0.0), id)
            assertTrue(
                displays.all { replacement.connection.spawns(it.entityId).isEmpty() },
                "A different connection must not inherit the old admission predicate",
            )
            handle.advanceAnimation(1000)
            assertTrue(displays.all { replacement !in it.viewers })
            replacement.teleport(Pos(60.0, 0.0, 0.0)).join()
            handle.advanceAnimation(1200)
            assertTrue(displays.all { replacement in it.viewers })
            handle.close()
        }
    }

    @Test
    fun `a reconnect cannot inherit the previous connection's scale or glow`() {
        RendererFixture().use { fixture ->
            val id = java.util.UUID.randomUUID()
            val first = fixture.player(uuid = id)
            val handle = create(fixture)
            handle.applyViewerState(first, NpcViewerState(2.0, true))
            first.remove()
            val replacement = fixture.player(uuid = id)
            handle.advanceAnimation(1000)
            for (display in fixture.instance.entities.filterIsInstance<NpcItemDisplay>()) {
                val baseline = (display.entityMeta as ItemDisplayMeta).scale
                val scales =
                    replacement.connection.packets
                        .filterIsInstance<EntityMetaDataPacket>()
                        .filter { it.entityId() == display.entityId }
                        .mapNotNull { it.entries()[MetadataDef.Display.SCALE.index()]?.value() }
                assertTrue(scales.isNotEmpty())
                assertTrue(
                    scales.all { it == baseline },
                    "A new connection starts with default visuals",
                )
                assertTrue(replacement.connection.flags(display.entityId).all { it and 0x40 == 0 })
            }
            handle.close()
        }
    }

    private val model =
        modelFrom(
            """{"schemaVersion":1,"asset":"grounds:npc/test/performance","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"body","name":"body","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},{"id":"head","name":"head","parent":"body","origin":[0,1.5,0],"rotation":[0,0,0]}],"parts":[{"bone":"body","model":"grounds:npc_parts/test/body","center":[0,0.7,0],"pivot":[0,0.7,0],"rotation":[0,0,0]},{"bone":"head","model":"grounds:npc_parts/test/head","center":[0,1.8,0],"pivot":[0,1.8,0],"rotation":[0,0,0]}],"animations":{"idle":{"loop":"loop","length":2,"tracks":{}},"wave":{"loop":"once","length":2,"tracks":{"body":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":2,"value":[0,2,0],"interpolation":"linear"}]}}}},"sounds":{}}"""
        )

    private fun modelFrom(json: String) =
        (NpcModelJson.decode(json.toByteArray()) as NpcDecodeResult.Success).model

    private fun create(fixture: RendererFixture): NpcEngineHandle {
        val lock = Any()
        val parts =
            model.parts.map { part ->
                NpcItemDisplay(lock).also { display ->
                    display.updateViewableRule { false }
                    display.setInstance(fixture.instance, Pos.ZERO).join()
                    display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                        meta.setViewRange(1.25f)
                    }
                }
            }
        return AnimatedNpcHandle(
                lock,
                model,
                parts,
                sceneTransform(),
                NpcSoundSink { _, _, _ -> },
                NpcRenderConfig(idleClip = "idle", variationClip = "idle2"),
            )
            .also { it.publishVisible() }
    }

    @Test
    fun `translation animation does not resend unchanged rotations`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle = create(fixture)
            fixture.instance.entities.filterIsInstance<NpcItemDisplay>().forEach {
                it.addViewer(player)
            }
            handle.startAnimation("wave", 0)
            player.connection.packets.clear()
            handle.advanceAnimation(500)
            val updates = player.connection.packets.filterIsInstance<EntityMetaDataPacket>()
            assertEquals(2, updates.size)
            updates.forEach {
                assertTrue(
                    setOf(
                            MetadataDef.Display.TRANSLATION.index(),
                            MetadataDef.Display.INTERPOLATION_DELAY.index(),
                            MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index(),
                        )
                        .containsAll(it.entries().keys)
                )
                assertTrue(MetadataDef.Display.TRANSLATION.index() in it.entries())
                assertTrue(MetadataDef.Display.INTERPOLATION_DELAY.index() in it.entries())
            }
            handle.close()
        }
    }

    @Test
    fun `body yaw never teleports stationary display anchors`() {
        RendererFixture().use { fixture ->
            val handle = create(fixture)
            var teleports = 0
            fixture.instance.entities.filterIsInstance<NpcItemDisplay>().forEach { display ->
                display.eventNode().addListener(EntityTeleportEvent::class.java) { teleports++ }
            }
            val original = sceneTransform()
            handle.applyTransform(
                original.copy(
                    root = original.root.copy(rotationDegrees = NpcVector3(0.0, 45.0, 0.0))
                )
            )
            assertEquals(0, teleports)
            handle.close()
        }
    }

    @Test
    fun `decorative displays do not simulate physics`() {
        RendererFixture().use { fixture ->
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            assertEquals(2, displays.size)
            assertTrue(displays.all { !it.hasPhysics() })
            handle.close()
        }
    }

    @Test
    fun `animation head look and body follow share a bounded update cadence`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle = create(fixture)
            fixture.instance.entities.filterIsInstance<NpcItemDisplay>().forEach {
                it.addViewer(player)
            }
            handle.startAnimation("wave", 0)
            player.connection.packets.clear()
            for (tick in 1..20) {
                handle.advanceAnimation(tick * 50L)
                val original = sceneTransform()
                handle.applyTransform(
                    original.copy(
                        root =
                            original.root.copy(
                                rotationDegrees = NpcVector3(0.0, tick.toDouble(), 0.0)
                            )
                    )
                )
                handle.applyHeadLook(tick * 2.0, 0.0)
            }
            val updates = player.connection.packets.filterIsInstance<EntityMetaDataPacket>()
            assertTrue(
                updates.size in 1..10,
                "Two parts emitted ${updates.size} updates in one second",
            )
            handle.close()
        }
    }

    @Test
    fun `NPC is visible at fifty blocks during initial attachment`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            assertTrue(displays.all { player in it.viewers })
            assertTrue(displays.all { player.connection.spawns(it.entityId).size == 1 })
            handle.close()
        }
    }

    @Test
    fun `spawn metadata allows the client to render the entire retained range`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            for (display in displays) {
                val viewRange =
                    player.connection.packets
                        .filterIsInstance<EntityMetaDataPacket>()
                        .filter { it.entityId() == display.entityId }
                        .mapNotNull {
                            it.entries()[MetadataDef.Display.VIEW_RANGE.index()]?.value() as? Float
                        }
                        .lastOrNull() ?: 1.0f
                assertTrue(
                    viewRange >= 1.25f,
                    "Client needs visibility headroom beyond the retained range",
                )
            }
            handle.close()
        }
    }

    @Test
    fun `viewer remains across the admission boundary and leaves beyond the retention range`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(60.0, 0.0, 0.0))
            val handle = create(fixture)
            val body = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().first()
            assertTrue(player in body.viewers)
            player.connection.packets.clear()
            for (distance in listOf(66.0, 63.0, 70.0, 64.0)) {
                player.teleport(Pos(distance, 0.0, 0.0)).join()
                handle.advanceAnimation(500)
                assertTrue(player in body.viewers, "Viewer dropped at $distance blocks")
            }
            assertTrue(player.connection.spawns(body.entityId).isEmpty())
            player.teleport(Pos(73.0, 0.0, 0.0)).join()
            handle.advanceAnimation(1000)
            assertFalse(player in body.viewers)
            player.teleport(Pos(68.0, 0.0, 0.0)).join()
            handle.advanceAnimation(1500)
            assertFalse(player in body.viewers, "Retention must not admit a new viewer")
            player.teleport(Pos(60.0, 0.0, 0.0)).join()
            handle.advanceAnimation(1750)
            assertTrue(player in body.viewers)
            handle.close()
        }
    }

    @Test
    fun `distant NPC uses two animation updates per second and matching interpolation`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation("wave", 0)
            player.connection.packets.clear()
            for (tick in 1..20) handle.advanceAnimation(tick * 50L)
            val updates = player.connection.packets.filterIsInstance<EntityMetaDataPacket>()
            assertEquals(4, updates.size, "Two animated parts should each update twice")
            assertEquals(
                10,
                (body.entityMeta as ItemDisplayMeta).transformationInterpolationDuration,
            )
            assertEquals(1.7, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            handle.close()
        }
    }

    @Test
    fun `moving between distance bands adjusts the next update and interpolation`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation("wave", 0)
            player.connection.packets.clear()
            handle.advanceAnimation(250)
            assertTrue(player.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            player.teleport(Pos(8.0, 0.0, 0.0)).join()
            handle.advanceAnimation(300)
            assertEquals(
                4,
                (body.entityMeta as ItemDisplayMeta).transformationInterpolationDuration,
            )
            assertEquals(1.0, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            player.teleport(Pos(50.0, 0.0, 0.0)).join()
            player.connection.packets.clear()
            handle.advanceAnimation(600)
            // Rate transitions restore the current complete pose before the slower deadline.
            assertEquals(2, player.connection.packets.filterIsInstance<EntityMetaDataPacket>().size)
            assertEquals(1.3, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            player.connection.packets.clear()
            handle.advanceAnimation(1000)
            assertTrue(player.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            handle.advanceAnimation(1100)
            assertEquals(2, player.connection.packets.filterIsInstance<EntityMetaDataPacket>().size)
            assertEquals(
                10,
                (body.entityMeta as ItemDisplayMeta).transformationInterpolationDuration,
            )
            assertEquals(1.8, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            handle.close()
        }
    }

    @Test
    fun `retention range cannot keep a viewer on the vertically separated lobby floor`() {
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            assertTrue(displays.all { player in it.viewers })
            player.teleport(Pos(50.0, 33.0, 0.0)).join()
            handle.startAnimation("wave", 0)
            assertTrue(displays.all { player !in it.viewers })
            player.connection.packets.clear()
            handle.advanceAnimation(1000)
            assertTrue(player.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            handle.close()
        }
    }

    @Test
    fun `vertical station distance excludes viewers in the same chunk`() {
        RendererFixture().use { fixture ->
            // The Stage platform is 33 blocks above the lobby floor.
            val far = fixture.player(Pos(0.0, 33.0, 0.0))
            val handle = create(fixture)
            val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
            assertTrue(displays.all { far !in it.viewers })
            far.connection.packets.clear()
            handle.startAnimation("wave", 0)
            handle.advanceAnimation(500)
            assertTrue(far.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            handle.close()
        }
    }

    @Test
    fun `invisible animation resumes at current elapsed time when a viewer approaches`() {
        RendererFixture().use { fixture ->
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation("wave", 0)
            handle.advanceAnimation(500)
            assertEquals(0.7, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            val player = fixture.player()
            handle.advanceAnimation(1000)
            assertEquals(1.7, (body.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            assertTrue(player in body.viewers)
            handle.close()
        }
    }

    @Test
    fun `small head targets accumulate until a visible angle changes`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle = create(fixture)
            fixture.instance.entities.filterIsInstance<NpcItemDisplay>().forEach {
                it.addViewer(player)
            }
            handle.startAnimation("idle", 0)
            player.connection.packets.clear()
            for (tick in 1..3) {
                handle.applyHeadLook(tick * 0.5, 0.0)
                handle.advanceAnimation(tick * 200L)
            }
            assertTrue(player.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            handle.applyHeadLook(2.0, 0.0)
            handle.advanceAnimation(800)
            val updates = player.connection.packets.filterIsInstance<EntityMetaDataPacket>()
            assertEquals(1, updates.size, "Only the head subtree should change")
            handle.close()
        }
    }

    @Test
    fun `range reentry restores current pose and personal scale and glow`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation("wave", 0)
            handle.applyViewerState(player, NpcViewerState(2.0, true))
            player.teleport(Pos(0.0, 60.0, 0.0)).join()
            handle.advanceAnimation(500)
            assertFalse(player in body.viewers)
            player.connection.packets.clear()
            handle.advanceAnimation(1000)
            assertTrue(player.connection.packets.filterIsInstance<EntityMetaDataPacket>().isEmpty())
            player.teleport(Pos.ZERO).join()
            handle.advanceAnimation(1500)
            assertTrue(player in body.viewers)
            val updates =
                player.connection.packets.filterIsInstance<EntityMetaDataPacket>().filter {
                    it.entityId() == body.entityId
                }
            val translation =
                updates
                    .mapNotNull {
                        it.entries()[MetadataDef.Display.TRANSLATION.index()]?.value()
                            as? net.minestom.server.coordinate.Point
                    }
                    .last()
            assertEquals(4.4, translation.y(), 1e-6)
            assertTrue(player.connection.flags(body.entityId).last() and 0x40 != 0)
            handle.close()
        }
    }

    @Test
    fun `visibility keeps updating while animation is stopped`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle = create(fixture)
            val body = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().first()
            handle.stopAnimation(null)
            player.teleport(Pos(0.0, 60.0, 0.0)).join()
            repeat(5) { fixture.instance.tick(it * 50L) }
            assertFalse(player in body.viewers)
            player.teleport(Pos.ZERO).join()
            repeat(5) { fixture.instance.tick(250L + it * 50L) }
            assertTrue(player in body.viewers)
            handle.close()
        }
    }

    @Test
    fun `stopping animation commits the final queued head target`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val handle = create(fixture)
            val head =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().maxBy { it.entityId }
            val meta = head.entityMeta as ItemDisplayMeta
            handle.startAnimation("idle", 0)
            val before = meta.leftRotation.copyOf()
            handle.applyHeadLook(30.0, 0.0)
            handle.stopAnimation("idle")
            assertFalse(
                before.contentEquals(meta.leftRotation),
                "Stopped NPC retained an old head target",
            )
            val stopped = meta.leftRotation.copyOf()
            handle.applyHeadLook(30.0, 0.0)
            assertArrayEquals(stopped, meta.leftRotation)
            handle.close()
        }
    }

    @Test
    fun `a player at the same coordinates in another instance is outside station range`() {
        RendererFixture().use { first ->
            RendererFixture().use { other ->
                val player = first.player()
                val policy = NpcRenderPolicy(NpcRenderConfig())
                assertTrue(policy.canView(player, first.instance, NpcVector3(0.0, 0.0, 0.0)))
                assertFalse(policy.canView(player, other.instance, NpcVector3(0.0, 0.0, 0.0)))
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun boot() {
            MinecraftServer.init()
        }
    }
}
