package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.NpcPresentationPacketTest.Companion.drive
import java.lang.management.ManagementFactory
import java.util.UUID
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.world.DimensionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcPresentationPerformanceTest {
    @Test
    fun `private sampling adds no world player enumeration or unchanged-pose packets`() {
        MinecraftServer.init()
        val instance = CountingInstance()
        MinecraftServer.getInstanceManager().registerInstance(instance)
        RendererFixture(instance).use { fixture ->
            val players = (0..32).map { fixture.player(Pos(it / 32.0, 42.0, 0.0)) }
            val root =
                NpcRenderer.prepare(
                    NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
                )
            val parent = (drive(fixture, root.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            repeat(8) { instance.tick(0) }
            fun measure(): Pair<Int, Long> {
                players.forEach { it.connection.packets.clear() }
                instance.enumerations = 0
                val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
                val before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                repeat(20) { instance.tick(0) }
                val bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before
                assertTrue(
                    players.all { it.connection.packets.isEmpty() },
                    "Unchanged model frames must not send packets",
                )
                return instance.enumerations to bytes
            }
            val baseline = measure()
            val handles = ArrayList<NpcPresentationHandle>()
            try {
                for (count in listOf(1, 8, 32)) {
                    while (handles.size < count) {
                        val start =
                            NpcPresentations.prepare(
                                parent,
                                NpcPresentationOptions(
                                    players[handles.size],
                                    clock = NpcMonotonicClock { 10_000_000_000 },
                                ),
                            )
                        val handle =
                            (drive(fixture, start.ready()) as NpcPresentationResult.Ready).handle
                        drive(fixture, handle.publish())
                        drive(fixture, handle.playClip("idle"))
                        handles += handle
                    }
                    repeat(8) { instance.tick(0) }
                    val actual = measure()
                    assertEquals(
                        baseline.first,
                        actual.first,
                        "Private copies must enumerate only their one participant",
                    )
                    assertEquals(
                        2 * (count + 1),
                        instance.entities.filterIsInstance<NpcItemDisplay>().size,
                    )
                    println(
                        "N7 personal=$count parts=2 worldEnumerations=${actual.first} allocatedPerTick=${actual.second / 20} baselinePerTick=${baseline.second / 20}"
                    )
                }
            } finally {
                root.cancel().toCompletableFuture().join()
            }
            assertTrue(instance.entities.filterIsInstance<NpcItemDisplay>().isEmpty())
        }
    }

    @Test
    fun `50-part groups respect the real active part budget and allow an explicit larger profile`() {
        for ((count, limit) in listOf(20 to 1024, 32 to 1600)) {
            MinecraftServer.init()
            RendererFixture().use { fixture ->
                val players = (0..count).map { fixture.player(Pos(it / 32.0, 42.0, 0.0)) }
                val base = personalTestModel()
                val model =
                    gg.grounds.npc.core.NpcModel(
                        base.id,
                        base.bounds,
                        base.bones,
                        List(50) { base.parts[0] },
                        base.animations,
                        base.sounds,
                    )
                val root =
                    NpcRenderer.prepare(
                        NpcSpawnRequest(fixture.instance, model, NpcLifecycleTest.transform()),
                        NpcPresentationLimits(32, limit),
                    )
                val parent = (drive(fixture, root.ready()) as NpcPrepareResult.Ready).handle
                drive(fixture, parent.publish())
                val began = System.nanoTime()
                try {
                    for (player in players.take(count)) {
                        val start =
                            NpcPresentations.prepare(
                                parent,
                                NpcPresentationOptions(
                                    player,
                                    clock = NpcMonotonicClock { 10_000_000_000 },
                                ),
                            )
                        val handle =
                            (drive(fixture, start.ready()) as NpcPresentationResult.Ready).handle
                        drive(fixture, handle.publish())
                    }
                    val refused =
                        NpcPresentations.prepare(parent, NpcPresentationOptions(players.last()))
                    assertInstanceOf(
                        NpcPresentationResult.Rejected::class.java,
                        refused
                            .ready()
                            .toCompletableFuture()
                            .get(1, java.util.concurrent.TimeUnit.SECONDS),
                    )
                    assertEquals(
                        50 * (count + 1),
                        fixture.instance.entities.filterIsInstance<NpcItemDisplay>().size,
                    )
                    players.forEach { it.connection.packets.clear() }
                    val bean =
                        ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
                    val before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                    repeat(8) { fixture.instance.tick(0) }
                    val bytes =
                        bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before
                    assertTrue(players.all { it.connection.packets.isEmpty() })
                    println(
                        "N7 personal=$count parts=50 budget=$limit allocatedPerTick=${bytes / 8} prepareMillis=${(System.nanoTime() - began) / 1_000_000}"
                    )
                } finally {
                    root
                        .cancel()
                        .toCompletableFuture()
                        .get(3, java.util.concurrent.TimeUnit.SECONDS)
                }
                assertTrue(fixture.instance.entities.filterIsInstance<NpcItemDisplay>().isEmpty())
            }
        }
    }

    private class CountingInstance : InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD) {
        var enumerations = 0

        override fun getPlayers(): MutableSet<Player> {
            val actual = super.getPlayers()
            return object : MutableSet<Player> by actual {
                override fun iterator(): MutableIterator<Player> {
                    enumerations++
                    return actual.iterator()
                }
            }
        }
    }
}
