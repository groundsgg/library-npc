package gg.grounds.npc.minestom

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.MetadataDef
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcPresentationPacketTest {
    @Test
    fun `private publication inherits only the owner's scale and glow`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val a = fixture.player(Pos(0.0, 42.0, 0.0))
            val b = fixture.player(Pos(1.0, 42.0, 0.0))
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        fixture.instance,
                        NpcLifecycleTest.model(),
                        NpcLifecycleTest.transform(),
                    )
                )
            val parent = (drive(fixture, start.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            drive(fixture, parent.applyViewerState(a, NpcViewerState(2.0, true)))
            val common = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
            val personalStart = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
            try {
                val personal =
                    (drive(fixture, personalStart.ready()) as NpcPresentationResult.Ready).handle
                drive(fixture, personal.publish())
                val privateParts =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().filter {
                        it !in common
                    }
                assertEquals(2, privateParts.size)
                for (part in privateParts) {
                    val scale =
                        a.connection.packets
                            .filterIsInstance<EntityMetaDataPacket>()
                            .filter { it.entityId() == part.entityId }
                            .mapNotNull {
                                it.entries()[MetadataDef.Display.SCALE.index()]?.value() as? Point
                            }
                            .last()
                    assertEquals(2.0, scale.x(), 0.000001)
                    assertTrue(a.connection.flags(part.entityId).last() and 0x40 != 0)
                    assertTrue(b.connection.spawns(part.entityId).isEmpty())
                }
            } finally {
                personalStart.cancel()
                start.cancel()
            }
        }
    }

    @Test
    fun `shared viewer updates reach the active private display without restarting its clip`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val a = fixture.player(Pos(0.0, 42.0, 0.0))
            val b = fixture.player(Pos(1.0, 42.0, 0.0))
            val clock = java.util.concurrent.atomic.AtomicLong(10_000_000_000)
            var sounds = 0
            val parentStart =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        fixture.instance,
                        personalTestModel(true),
                        NpcLifecycleTest.transform(),
                    )
                )
            val parent = (drive(fixture, parentStart.ready()) as NpcPrepareResult.Ready).handle
            drive(fixture, parent.publish())
            val common = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
            val start =
                NpcPresentations.prepare(
                    parent,
                    NpcPresentationOptions(
                        a,
                        clock = NpcMonotonicClock(clock::get),
                        sounds = NpcViewerSoundSink { _, _, _ -> sounds++ },
                    ),
                )
            try {
                val personal = (drive(fixture, start.ready()) as NpcPresentationResult.Ready).handle
                drive(fixture, personal.publish())
                drive(fixture, personal.playClip("wave"))
                drive(fixture, personal.applyHeadLook(20.0, -10.0))
                clock.set(10_250_000_000)
                fixture.instance.tick(0)
                val parts =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().filter {
                        it !in common
                    }
                fun scale(part: NpcItemDisplay) =
                    a.connection.packets
                        .filterIsInstance<EntityMetaDataPacket>()
                        .filter { it.entityId() == part.entityId }
                        .mapNotNull {
                            it.entries()[MetadataDef.Display.SCALE.index()]?.value() as? Point
                        }
                        .last()
                        .x()
                drive(fixture, parent.applyViewerState(a, NpcViewerState(2.0, true)))
                assertTrue(parts.all { kotlin.math.abs(scale(it) - 2.0) < 0.000001 })
                assertTrue(parts.all { a.connection.flags(it.entityId).last() and 0x40 != 0 })
                drive(fixture, parent.clearViewerState(a))
                assertTrue(parts.all { kotlin.math.abs(scale(it) - 1.0) < 0.000001 })
                assertTrue(parts.all { a.connection.flags(it.entityId).last() and 0x40 == 0 })
                assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
                assertEquals(NpcPresentationChannel.PRIVATE, personal.snapshot().headMode)
                assertEquals(1, sounds)
                assertTrue(parts.all { b.connection.spawns(it.entityId).isEmpty() })
            } finally {
                start.cancel()
                parentStart.cancel()
            }
        }
    }

    companion object {
        internal fun <T> drive(fixture: RendererFixture, stage: CompletionStage<T>): T {
            val future = stage.toCompletableFuture()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!future.isDone && System.nanoTime() < deadline) {
                fixture.instance.tick(0)
                Thread.yield()
            }
            return future.get(1, TimeUnit.SECONDS)
        }
    }
}
