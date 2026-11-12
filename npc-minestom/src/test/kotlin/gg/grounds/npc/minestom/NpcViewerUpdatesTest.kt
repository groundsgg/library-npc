package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.util.UUID
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.packet.server.CachedPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.world.DimensionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NpcViewerUpdatesTest {
    private val model =
        parseTestModel(
            """{"schemaVersion":1,"asset":"grounds:npc/test/cohorts","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"body","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},{"id":"head","name":"head","parent":"body","origin":[0,1.5,0],"rotation":[0,0,0]}],"parts":[{"bone":"body","model":"grounds:npc_parts/test/body","center":[0,0.7,0],"pivot":[0,0.7,0],"rotation":[0,0,0]},{"bone":"head","model":"grounds:npc_parts/test/head","center":[0,1.8,0],"pivot":[0,1.8,0],"rotation":[0,0,0]}],"animations":{"wave":{"loop":"loop","length":2,"tracks":{"body":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":2,"value":[0,2,0],"interpolation":"linear"}]}}}},"sounds":{}}"""
        )

    private fun create(fixture: RendererFixture): HeadLookRenderedAssetHandle {

        return EngineTestRenderers(mapOf(model.id to model))
            .rendererFor(model.id, AssetKind.NPC_BODY)!!
            .create(
                SceneAssetRenderContext(
                    fixture.instance,
                    LocalId("cohorts"),
                    null,
                    model.id,
                    sceneTransform(),
                )
            )
            .toCompletableFuture()
            .join() as HeadLookRenderedAssetHandle
    }

    private fun TestPlayer.updates() = connection.packets.filterIsInstance<EntityMetaDataPacket>()

    private fun EntityMetaDataPacket.translation() =
        entries()[MetadataDef.Display.TRANSLATION.index()]?.value() as? Point

    @Test
    fun `near medium and far viewers receive their own cadence from one shared pose`() {
        RendererFixture().use { fixture ->
            val viewers =
                listOf(
                    fixture.player(),
                    fixture.player(Pos(24.0, 0.0, 0.0)),
                    fixture.player(Pos(50.0, 0.0, 0.0)),
                )
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            for ((viewer, duration) in viewers.zip(listOf(4, 5, 10))) {
                val initializedDuration =
                    viewer
                        .updates()
                        .mapNotNull {
                            it.entries()[
                                    MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION
                                        .index()]
                                ?.value() as? Int
                        }
                        .last()
                assertEquals(duration, initializedDuration)
            }
            viewers.forEach { it.connection.packets.clear() }
            for (tick in 1..20) handle.advanceAnimation(tick * 50L)
            assertEquals(listOf(10, 8, 4), viewers.map { it.updates().size })
            assertTrue(
                viewers
                    .flatMap { it.updates() }
                    .all {
                        MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index() !in
                            it.entries()
                    },
                "A cohort duration is constant until its full initialization/transition snapshot",
            )
            handle.close()
        }
    }

    @Test
    fun `far flush retains a one time rotation omitted from later common deltas`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            far.connection.packets.clear()
            handle.applyHeadLook(35.0, 0.0)
            handle.advanceAnimation(200)
            val head =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().maxBy { it.entityId }
            val expected = (head.entityMeta as ItemDisplayMeta).leftRotation.copyOf()
            assertTrue(far.updates().isEmpty(), "Far viewer was sent the near frame")
            handle.advanceAnimation(400)
            handle.advanceAnimation(500)
            val update = far.updates().single { it.entityId() == head.entityId }
            assertArrayEquals(
                expected,
                update.entries()[MetadataDef.Display.ROTATION_LEFT.index()]?.value() as? FloatArray,
            )
            handle.close()
        }
    }

    @Test
    fun `stop flushes final pose and drains queued changes before the next deadline`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            far.connection.packets.clear()
            handle.stopAnimation(LocalId("wave"))
            assertEquals(
                0.7,
                far.updates().last { it.entityId() == body.entityId }.translation()!!.y(),
                1e-6,
            )
            far.connection.packets.clear()
            handle.advanceAnimation(1000)
            assertTrue(far.updates().isEmpty())
            handle.close()
        }
    }

    @Test
    fun `backward seek immediately delivers a coherent pose and rebases the deadline`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            handle.advanceAnimation(400)
            far.connection.packets.clear()
            handle.advanceAnimation(100)
            assertEquals(
                0.8,
                far.updates().last { it.entityId() == body.entityId }.translation()!!.y(),
                1e-6,
            )
            far.connection.packets.clear()
            handle.advanceAnimation(300)
            handle.advanceAnimation(500)
            handle.advanceAnimation(600)
            assertEquals(2, far.updates().size)
            handle.close()
        }
    }

    @Test
    fun `joining an occupied far cohort does not postpone existing viewers deadline`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            far.connection.packets.clear()
            fixture.player(Pos(48.0, 0.0, 0.0))
            handle.advanceAnimation(400)
            val npcIds =
                fixture.instance.entities
                    .filterIsInstance<NpcItemDisplay>()
                    .map { it.entityId }
                    .toSet()
            assertTrue(far.updates().none { it.entityId() in npcIds })
            handle.advanceAnimation(500)
            assertEquals(2, far.updates().count { it.entityId() in npcIds })
            handle.close()
        }
    }

    @Test
    fun `a published near delta is not mutated while the far cohort accumulates later fields`() {
        RendererFixture().use { fixture ->
            val near = fixture.player()
            fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            val body =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy { it.entityId }
            handle.startAnimation(LocalId("wave"), 0)
            near.connection.packets.clear()
            handle.advanceAnimation(200)
            val first = near.updates().single { it.entityId() == body.entityId }
            val original = first.entries().toMap()
            handle.applyTransform(
                sceneTransform().let {
                    it.copy(root = it.root.copy(rotationDegrees = EulerRotation(35.0, 0.0, 0.0)))
                }
            )
            handle.advanceAnimation(400)
            handle.advanceAnimation(500)
            assertEquals(original, first.entries())
            assertEquals(0.9, first.translation()!!.y(), 1e-6)
            handle.close()
        }
    }

    @Test
    fun `queued packet entries remain immutable across later cohort flushes`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            far.connection.packets.clear()
            handle.advanceAnimation(200)
            handle.advanceAnimation(400)
            handle.advanceAnimation(500)
            val packet = far.updates().first()
            val position = packet.translation()
            assertNotNull(position)
            handle.advanceAnimation(600)
            handle.advanceAnimation(800)
            handle.advanceAnimation(1000)
            assertEquals(position, packet.translation())
            assertFalse(packet.entries().isEmpty())
            handle.close()
        }
    }

    @Test
    fun `retained distance bands still determine the shared sampling interval`() {
        for ((start, retained, interval) in
            listOf(Triple(0.0, 17.0, 200L), Triple(24.0, 34.0, 250L))) {
            RendererFixture().use { fixture ->
                val player = fixture.player(Pos(start, 0.0, 0.0))
                val handle = create(fixture)
                handle.startAnimation(LocalId("wave"), 0)
                player.teleport(Pos(retained, 0.0, 0.0)).join()
                player.connection.packets.clear()
                handle.advanceAnimation(interval)
                assertEquals(2, player.updates().size)
                val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
                assertTrue(
                    displays.all {
                        (it.entityMeta as ItemDisplayMeta).transformationInterpolationDuration ==
                            (interval / 50).toInt()
                    }
                )
                handle.close()
            }
        }
    }

    @Test
    fun `immediate personal scale sends a complete pose with the slow viewers duration`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            handle.applyHeadLook(35.0, 0.0)
            handle.advanceAnimation(200)
            far.connection.packets.clear()
            handle.applyViewerState(far, SceneViewerVisualState(2.0, true))
            val poseFields =
                setOf(
                    MetadataDef.Display.TRANSLATION.index(),
                    MetadataDef.Display.SCALE.index(),
                    MetadataDef.Display.ROTATION_LEFT.index(),
                    MetadataDef.Display.ROTATION_RIGHT.index(),
                )
            assertEquals(2, far.updates().size)
            assertTrue(far.updates().all { it.entries().keys.containsAll(poseFields) })
            assertTrue(
                far.updates().all {
                    it.entries()[MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()]
                        ?.value() == 10
                }
            )
            val head =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().maxBy { it.entityId }
            val packet = far.updates().single { it.entityId() == head.entityId }
            assertEquals(
                (head.entityMeta as ItemDisplayMeta).translation.y() * 2,
                packet.translation()!!.y(),
                1e-6,
            )
            assertArrayEquals(
                (head.entityMeta as ItemDisplayMeta).leftRotation,
                packet.entries()[MetadataDef.Display.ROTATION_LEFT.index()]?.value() as? FloatArray,
            )
            handle.close()
        }
    }

    @Test
    fun `an empty far cohort discards its old deadline and reentry gets a current snapshot`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            far.teleport(Pos(100.0, 0.0, 0.0)).join()
            handle.advanceAnimation(400)
            far.teleport(Pos(50.0, 0.0, 0.0)).join()
            handle.advanceAnimation(450)
            assertTrue(far.updates().isNotEmpty())
            far.connection.packets.clear()
            handle.advanceAnimation(500)
            assertTrue(far.updates().isEmpty())
            handle.advanceAnimation(700)
            handle.advanceAnimation(950)
            assertEquals(2, far.updates().size)
            handle.close()
        }
    }

    @Test
    fun `closing a handle discards pending viewer updates`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            far.connection.packets.clear()
            handle.close()
            handle.advanceAnimation(1000)
            assertTrue(far.updates().isEmpty())
        }
    }

    @Test
    fun `highlight is immediate without advancing a slow viewers pending transform`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(200)
            far.connection.packets.clear()
            handle.applyViewerState(far, SceneViewerVisualState(1.0, true))
            assertEquals(2, far.updates().size)
            assertTrue(far.updates().all { it.entries().keys == setOf(0) })
            assertTrue(
                far.updates().all { (it.entries()[0]!!.value() as Byte).toInt() and 0x40 != 0 }
            )
            handle.close()
        }
    }

    @Test
    fun `identical recipients share a cached packet independently within each cohort`() {
        RendererFixture().use { fixture ->
            val near = listOf(fixture.player(), fixture.player(Pos(2.0, 0.0, 0.0)))
            val far =
                listOf(fixture.player(Pos(50.0, 0.0, 0.0)), fixture.player(Pos(51.0, 0.0, 0.0)))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                (near + far).forEach {
                    it.connection.captureEnvelopes = true
                    it.connection.envelopes.clear()
                    it.connection.packets.clear()
                }
                handle.advanceAnimation(200)
                assertEquals(2, near[0].connection.envelopes.size)
                assertTrue(far.all { it.connection.envelopes.isEmpty() })
                near[0].connection.envelopes.zip(near[1].connection.envelopes).forEach { (a, b) ->
                    assertTrue(a is CachedPacket)
                    assertSame(a, b)
                }
                val retained = near[0].updates().first().entries().toMap()
                handle.advanceAnimation(400)
                handle.advanceAnimation(500)
                assertEquals(2, far[0].connection.envelopes.size)
                far[0].connection.envelopes.zip(far[1].connection.envelopes).forEach { (a, b) ->
                    assertTrue(a is CachedPacket)
                    assertSame(a, b)
                }
                assertEquals(retained, near[0].updates().first().entries())
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `equal viewer count still swaps leaving and entering players immediately`() {
        RendererFixture().use { fixture ->
            val leaving = fixture.player()
            val stable = fixture.player(Pos(50.0, 0.0, 0.0))
            val entering = fixture.player(Pos(100.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                leaving.teleport(Pos(100.0, 0.0, 0.0)).join()
                entering.teleport(Pos.ZERO).join()
                entering.connection.packets.clear()
                stable.connection.packets.clear()
                handle.advanceAnimation(200)
                val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
                for (display in fixture.instance.entities.filterIsInstance<NpcItemDisplay>()) {
                    assertFalse(leaving in display.viewers)
                    assertTrue(entering in display.viewers)
                    assertTrue(stable in display.viewers)
                    val state =
                        entering.updates().last {
                            it.entityId() == display.entityId && duration in it.entries()
                        }
                    assertEquals(7, state.entries().size)
                    assertEquals(4, state.entries()[duration]!!.value())
                }
                assertTrue(stable.updates().none { duration in it.entries() })
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `weak iteration during reconnect does not retain a stale shared sampling interval`() {
        class ReconnectingInstance : InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD) {
            var replacement: (() -> Player)? = null

            override fun getPlayers(): MutableSet<Player> {
                val action = replacement ?: return super.getPlayers()
                val original = super.getPlayers().single()
                return object : java.util.AbstractSet<Player>() {
                    override val size = 1

                    override fun iterator(): MutableIterator<Player> =
                        object : MutableIterator<Player> {
                            private var index = 0

                            override fun hasNext() = index < 2

                            override fun next(): Player =
                                when (index++) {
                                    0 -> original
                                    1 -> {
                                        replacement = null
                                        action()
                                    }
                                    else -> throw NoSuchElementException()
                                }

                            override fun remove() = throw UnsupportedOperationException()
                        }
                }
            }
        }
        val instance = ReconnectingInstance()
        MinecraftServer.getInstanceManager().registerInstance(instance)
        RendererFixture(instance = instance).use { fixture ->
            val original = fixture.player()
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                original.teleport(Pos(24.0, 0.0, 0.0)).join()
                lateinit var replacement: TestPlayer
                instance.replacement = {
                    original.remove()
                    fixture.player(Pos(50.0, 0.0, 0.0), original.uuid).also { replacement = it }
                }
                handle.advanceAnimation(200)
                replacement.connection.packets.clear()
                handle.advanceAnimation(400)
                handle.advanceAnimation(600)
                handle.advanceAnimation(700)
                val body =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy {
                        it.entityId
                    }
                val delivered = replacement.updates().last { it.entityId() == body.entityId }
                assertEquals(1.4, delivered.translation()!!.y(), 1e-6)
            } finally {
                instance.replacement = null
                handle.close()
            }
        }
    }

    @Test
    fun `candidate membership never sends a delta for a display not spawned to that viewer`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                val body =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy {
                        it.entityId
                    }
                body.updateViewableRule { it !== far }
                assertFalse(far in body.viewers)
                far.connection.packets.clear()
                handle.advanceAnimation(200)
                handle.advanceAnimation(500)
                assertEquals(1, far.updates().size)
                assertTrue(far.updates().none { it.entityId() == body.entityId })
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `single cohort preparation keeps the existing deadline after its deferred flush`() {
        RendererFixture().use { fixture ->
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                handle.advanceAnimation(200)
                fixture.player(Pos(48.0, 0.0, 0.0))
                far.connection.packets.clear()
                handle.advanceAnimation(400)
                assertTrue(far.updates().isEmpty())
                handle.advanceAnimation(500)
                assertEquals(2, far.updates().size)
                far.connection.packets.clear()
                handle.advanceAnimation(900)
                assertTrue(far.updates().isEmpty())
                handle.advanceAnimation(1000)
                assertEquals(2, far.updates().size)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `a homogeneous audience retains direct cached metadata delivery`() {
        RendererFixture().use { fixture ->
            val viewers = listOf(fixture.player(), fixture.player(Pos(2.0, 0.0, 0.0)))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                viewers.forEach {
                    it.connection.captureEnvelopes = true
                    it.connection.envelopes.clear()
                    it.connection.packets.clear()
                }
                val body =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().minBy {
                        it.entityId
                    }
                body.editEntityMeta(ItemDisplayMeta::class.java) { it.isInvisible = true }
                for (viewer in viewers) {
                    assertEquals(1, viewer.updates().size)
                    assertTrue(
                        (viewer.updates().single().entries().getValue(0).value() as Byte)
                            .toInt() and 0x20 != 0
                    )
                }
                val frame = viewers[0].connection.envelopes.single()
                assertTrue(frame is CachedPacket)
                assertSame(frame, viewers[1].connection.envelopes.single())
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `same uuid reconnect replaces cached recipients without postponing the occupied cohort`() {
        RendererFixture().use { fixture ->
            fixture.player()
            val original = fixture.player(Pos(50.0, 0.0, 0.0))
            val other = fixture.player(Pos(51.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                handle.advanceAnimation(200)
                val uuid = original.uuid
                original.remove()
                val replacement = fixture.player(Pos(50.0, 0.0, 0.0), uuid)
                assertNotSame(original, replacement)
                listOf(replacement, other).forEach { it.connection.packets.clear() }
                handle.advanceAnimation(400)
                handle.advanceAnimation(500)
                for (viewer in listOf(replacement, other)) {
                    val heights = viewer.updates().mapNotNull { it.translation()?.y() }.sorted()
                    assertEquals(2, heights.size)
                    assertEquals(1.1, heights[0], 1e-6)
                    assertEquals(2.2, heights[1], 1e-6)
                }
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `seek inside a suppressed render interval restores the current pose for all cohorts`() {
        RendererFixture().use { fixture ->
            val near = fixture.player()
            val far = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                handle.advanceAnimation(150)
                listOf(near, far).forEach { it.connection.packets.clear() }
                handle.advanceAnimation(100)
                for (viewer in listOf(near, far)) {
                    val heights = viewer.updates().mapNotNull { it.translation()?.y() }.sorted()
                    assertEquals(2, heights.size)
                    assertEquals(0.8, heights[0], 1e-6)
                    assertEquals(1.9, heights[1], 1e-6)
                }
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `cohort transition bounds player resolution to two traversals per display`() {
        class CountingInstance : InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD) {
            var counting = false
            var resolutions = 0

            override fun getEntityById(entityId: Int): net.minestom.server.entity.Entity? {
                if (counting) resolutions++
                return super.getEntityById(entityId)
            }
        }
        val instance = CountingInstance()
        MinecraftServer.getInstanceManager().registerInstance(instance)
        RendererFixture(instance = instance).use { fixture ->
            val viewers = List(12) { fixture.player() }
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                val displays = instance.entities.filterIsInstance<NpcItemDisplay>()
                viewers.forEach { it.teleport(Pos(24.0, 0.0, 0.0)).join() }
                viewers.forEach { it.connection.packets.clear() }
                instance.resolutions = 0
                instance.counting = true
                try {
                    handle.advanceAnimation(250)
                } finally {
                    instance.counting = false
                }
                val budget = 2 * viewers.size * displays.size
                assertTrue(
                    instance.resolutions <= budget,
                    "Viewer transition resolved player IDs ${instance.resolutions} times; budget=$budget",
                )
                val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
                viewers.forEach { viewer ->
                    displays.forEach { display ->
                        assertTrue(viewer in display.viewers)
                        val state =
                            viewer.updates().last {
                                it.entityId() == display.entityId && duration in it.entries()
                            }
                        assertEquals(5, state.entries()[duration]!!.value())
                    }
                }
            } finally {
                instance.counting = false
                handle.close()
            }
        }
    }

    @Test
    fun `rate transition snapshots each displays old viewers before admitting missing parts`() {
        RendererFixture().use { fixture ->
            val viewer = fixture.player(Pos(50.0, 0.0, 0.0))
            val handle = create(fixture)
            try {
                handle.startAnimation(LocalId("wave"), 0)
                val displays = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()
                assertEquals(2, displays.size)
                val missing = displays.minBy { it.entityId }
                missing.updateViewableRule { false }
                assertFalse(viewer in missing.viewers)
                viewer.teleport(Pos.ZERO).join()
                viewer.connection.packets.clear()
                handle.advanceAnimation(200)
                val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
                val poseFields =
                    setOf(
                        0,
                        MetadataDef.Display.INTERPOLATION_DELAY.index(),
                        duration,
                        MetadataDef.Display.TRANSLATION.index(),
                        MetadataDef.Display.SCALE.index(),
                        MetadataDef.Display.ROTATION_LEFT.index(),
                        MetadataDef.Display.ROTATION_RIGHT.index(),
                    )
                for (display in displays) {
                    assertTrue(viewer in display.viewers)
                    // Native spawn metadata can also have seven fields; distinguish the
                    // complete pose by its actual fields, including default scale/rotations.
                    val full =
                        viewer.updates().filter {
                            it.entityId() == display.entityId && it.entries().keys == poseFields
                        }
                    assertEquals(
                        1,
                        full.size,
                        "Only one explicit full pose per display on the transition",
                    )
                    assertEquals(7, full.single().entries().size)
                    assertEquals(4, full.single().entries()[duration]!!.value())
                }
            } finally {
                handle.close()
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
