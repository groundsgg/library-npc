package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.NpcPresentationPacketTest.Companion.drive
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcPresentationConnectionTest {
    @Test
    fun `same UUID reconnect never inherits the old presentation suppression or sound`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val id = UUID.randomUUID()
            val old = fixture.player(Pos(0.0, 42.0, 0.0), id)
            val observer = fixture.player(Pos(1.0, 42.0, 0.0))
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        fixture.instance,
                        personalTestModel(true),
                        NpcLifecycleTest.transform(),
                    )
                )
            val parent = (drive(fixture, start.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            val common = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
            val sounds = ArrayList<net.minestom.server.entity.Player>()
            val personalStart =
                NpcPresentations.prepare(
                    parent,
                    NpcPresentationOptions(
                        old,
                        sounds = NpcViewerSoundSink { player, _, _ -> sounds += player },
                    ),
                )
            val personal =
                (drive(fixture, personalStart.ready()) as NpcPresentationResult.Ready).handle
            drive(fixture, personal.publish())
            drive(fixture, personal.playClip("wave"))
            val copies =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().filter {
                    it !in common
                }
            old.remove()
            val replacement = fixture.player(Pos(0.0, 42.0, 0.0), id)
            repeat(4) { fixture.instance.tick(0) }
            assertTrue(personal.closed().toCompletableFuture().isDone)
            assertTrue(
                copies.all { replacement.connection.spawns(it.entityId).isEmpty() && it.isRemoved }
            )
            assertEquals(listOf(old), sounds)
            assertTrue(common.all { replacement in it.viewers && observer in it.viewers })
            assertEquals(
                common,
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet(),
            )
            start.cancel()
        }
    }

    @Test
    fun `instance switch expires the presentation and rejects cues on the old handle`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            RendererFixture().use { destination ->
                val player = fixture.player(Pos(0.0, 42.0, 0.0))
                var sounds = 0
                val start =
                    NpcRenderer.prepare(
                        NpcSpawnRequest(
                            fixture.instance,
                            personalTestModel(true),
                            NpcLifecycleTest.transform(),
                        )
                    )
                val parent = (drive(fixture, start.ready()) as NpcPrepareResult.Ready).handle
                drive(fixture, parent.publish())
                val personalStart =
                    NpcPresentations.prepare(
                        parent,
                        NpcPresentationOptions(
                            player,
                            sounds = NpcViewerSoundSink { _, _, _ -> sounds++ },
                        ),
                    )
                val personal =
                    (drive(fixture, personalStart.ready()) as NpcPresentationResult.Ready).handle
                drive(fixture, personal.publish())
                val switching = player.setInstance(destination.instance, Pos(0.0, 42.0, 0.0))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (!switching.isDone && System.nanoTime() < deadline) {
                    MinecraftServer.getSchedulerManager().process()
                    destination.instance.tick(0)
                    fixture.instance.tick(0)
                    Thread.yield()
                }
                switching.get(1, TimeUnit.SECONDS)
                val queued = personal.playClip("wave").toCompletableFuture()
                repeat(4) { fixture.instance.tick(0) }
                assertEquals(0, sounds)
                assertTrue(queued.isCompletedExceptionally)
                assertEquals(NpcPresentationPhase.CLOSED, personal.snapshot().phase)
                assertEquals(2, fixture.instance.entities.filterIsInstance<NpcItemDisplay>().size)
                assertTrue(
                    destination.instance.entities.filterIsInstance<NpcItemDisplay>().isEmpty()
                )
                start.cancel()
            }
        }
    }

    @Test
    fun `distance reentry retains private IDs and current pose without replaying sound`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(0.0, 42.0, 0.0))
            val clock = AtomicLong(10_000_000_000)
            var sounds = 0
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        fixture.instance,
                        personalTestModel(true),
                        NpcLifecycleTest.transform(),
                    )
                )
            val parent = (drive(fixture, start.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            val common = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
            val personalStart =
                NpcPresentations.prepare(
                    parent,
                    NpcPresentationOptions(
                        player,
                        clock = NpcMonotonicClock(clock::get),
                        sounds = NpcViewerSoundSink { _, _, _ -> sounds++ },
                    ),
                )
            try {
                val personal =
                    (drive(fixture, personalStart.ready()) as NpcPresentationResult.Ready).handle
                drive(fixture, personal.publish())
                drive(fixture, personal.playClip("wave"))
                val copies =
                    fixture.instance.entities
                        .filterIsInstance<NpcItemDisplay>()
                        .filter { it !in common }
                        .sortedBy { it.entityId }
                player.teleport(Pos(200.0, 42.0, 0.0)).get(1, TimeUnit.SECONDS)
                clock.set(10_500_000_000)
                repeat(4) { fixture.instance.tick(0) }
                assertTrue(copies.all { player !in it.viewers })
                player.teleport(Pos(0.0, 42.0, 0.0)).get(1, TimeUnit.SECONDS)
                clock.set(10_750_000_000)
                repeat(4) { fixture.instance.tick(0) }
                assertTrue(copies.all { player in it.viewers })
                assertTrue(common.all { player !in it.viewers })
                assertEquals(
                    1.45,
                    (copies[0].entityMeta as ItemDisplayMeta).translation.y(),
                    0.00001,
                )
                assertEquals(0.75, personal.snapshot().clipSeconds, 0.000001)
                assertEquals(1, sounds)
                assertEquals(4, fixture.instance.entities.filterIsInstance<NpcItemDisplay>().size)
            } finally {
                personalStart.cancel()
                start.cancel()
            }
        }
    }

    @Test
    fun `rejected personal publication releases parts without suppressing common visibility`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val player = fixture.player(Pos(0.0, 42.0, 0.0))
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        fixture.instance,
                        personalTestModel(),
                        NpcLifecycleTest.transform(),
                    )
                )
            val parent = (drive(fixture, start.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            val common = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
            val personalStart =
                NpcPresentations.prepare(
                    parent,
                    NpcPresentationOptions(
                        player,
                        publicationGuard = NpcPublicationGuard { false },
                    ),
                )
            try {
                val personal =
                    (drive(fixture, personalStart.ready()) as NpcPresentationResult.Ready).handle
                assertThrows(Exception::class.java) { drive(fixture, personal.publish()) }
                assertTrue(personal.closed().toCompletableFuture().isDone)
                assertEquals(
                    common,
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet(),
                )
                assertTrue(common.all { player in it.viewers })
            } finally {
                personalStart.cancel()
                start.cancel()
            }
        }
    }
}
