package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import gg.grounds.npc.minestom.NpcPresentationPacketTest.Companion.drive
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.entity.EntityTeleportEvent
import net.minestom.server.instance.*
import net.minestom.server.world.DimensionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcPresentationReviewRegressionTest {
    @Test
    fun `hidden disconnect releases capacity without an attempted publish`() {
        setup(limits = NpcPresentationLimits(1, 2)).use { f ->
            val hidden = f.personal()
            f.player.remove()
            repeat(25) { f.fixture.instance.tick(0) }
            assertEquals(NpcPresentationPhase.CLOSED, hidden.snapshot().phase)
            assertEquals(2, f.parts().size)
            val replacement = NpcPresentations.prepare(f.parent, NpcPresentationOptions(f.other))
            assertInstanceOf(
                NpcPresentationResult.Ready::class.java,
                drive(f.fixture, replacement.ready()),
            )
        }
    }

    @Test
    fun `leaving and returning between samples cannot revive a hidden or active lease`() {
        for (active in listOf(false, true)) setup().use { f ->
            val personal = f.personal()
            if (active) drive(f.fixture, personal.publish())
            val destination = MinecraftServer.getInstanceManager().createInstanceContainer()
            destination.loadChunk(0, 0).join()
            f.player.setInstance(destination, Pos(0.0, 42.0, 0.0)).join()
            f.player.setInstance(f.fixture.instance, Pos(0.0, 42.0, 0.0)).join()
            assertThrows(Exception::class.java) { drive(f.fixture, personal.publish()) }
            assertTrue(personal.closed().toCompletableFuture().isDone)
            assertEquals(2, f.parts().size)
            MinecraftServer.getInstanceManager().unregisterInstance(destination)
        }
    }

    @Test
    fun `departure before construction releases its reservation without another tick`() {
        setup(limits = NpcPresentationLimits(1, 2)).use { f ->
            val pending = NpcPresentations.prepare(f.parent, NpcPresentationOptions(f.player))
            f.player.remove()
            assertInstanceOf(
                NpcPresentationResult.Rejected::class.java,
                pending.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
            )
            pending.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(2, f.parts().size)
            assertInstanceOf(
                NpcPresentationResult.Ready::class.java,
                drive(
                    f.fixture,
                    NpcPresentations.prepare(f.parent, NpcPresentationOptions(f.other)).ready(),
                ),
            )
        }
    }

    @Test
    fun `revoked personal generation cannot accept another cue or sound`() {
        setup().use { f ->
            val current = AtomicBoolean(true)
            var sounds = 0
            val personal =
                f.personal(
                    NpcPresentationOptions(
                        f.player,
                        publicationGuard =
                            NpcPublicationGuard { op ->
                                if (!current.get()) false
                                else {
                                    op.run()
                                    true
                                }
                            },
                        sounds = NpcViewerSoundSink { _, _, _ -> sounds++ },
                    )
                )
            drive(f.fixture, personal.publish())
            current.set(false)
            assertThrows(Exception::class.java) { drive(f.fixture, personal.playClip("wave")) }
            assertEquals(0, sounds)
            personal.closed().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(2, f.parts().size)
            assertTrue(f.parts().all { f.player in it.viewers && f.other in it.viewers })
        }
    }

    @Test
    fun `handover and restoration carry current retention admission`() {
        setup().use { f ->
            f.player.teleport(Pos(68.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            assertTrue(f.parts().all { f.player in it.viewers })
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            assertEquals(2, f.parts().count { f.player in it.viewers })
            personal.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertTrue(f.parts().all { f.player in it.viewers })
        }
        setup().use { f ->
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            f.player.teleport(Pos(100.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            f.player.teleport(Pos(68.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            assertTrue(f.parts().none { f.player in it.viewers })
            personal.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertTrue(
                f.parts().none { f.player in it.viewers },
                "Lost retention must not be restored from an obsolete entry snapshot",
            )
        }
    }

    @Test
    fun `current private admission is restored when it was acquired after handover`() {
        setup().use { f ->
            val personal = f.personal()
            f.player.teleport(Pos(100.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            drive(f.fixture, personal.publish())
            f.player.teleport(Pos(0.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            f.player.teleport(Pos(68.0, 42.0, 0.0)).join()
            repeat(4) { f.fixture.instance.tick(0) }
            assertEquals(2, f.parts().count { f.player in it.viewers })
            personal.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertTrue(f.parts().all { f.player in it.viewers })
        }
    }

    @Test
    fun `backward seek before a shared variation keeps a following presentation alive`() {
        setup(
                config =
                    NpcRenderConfig(
                        idleClip = "idle",
                        variationClip = "wave",
                        variationDelayMillis = 100,
                        nearIntervalMillis = 50,
                        midIntervalMillis = 50,
                        farIntervalMillis = 50,
                    )
            )
            .use { f ->
                f.player.teleport(Pos(10.0, 42.0, 0.0)).join()
                f.other.teleport(Pos(11.0, 42.0, 0.0)).join()
                val personal = f.personal()
                drive(f.fixture, personal.publish())
                drive(f.fixture, personal.applyHeadLook(20.0, 0.0))
                drive(f.fixture, f.parent.startAnimation("idle", 0))
                drive(f.fixture, f.parent.advanceAnimation(100))
                f.fixture.instance.tick(0)
                assertEquals("wave", personal.snapshot().clipId)
                drive(f.fixture, f.parent.advanceAnimation(50))
                f.fixture.instance.tick(0)
                assertEquals(NpcPresentationPhase.ACTIVE, personal.snapshot().phase)
                assertEquals("idle", personal.snapshot().clipId)
                assertEquals(0.05, personal.snapshot().clipSeconds, 0.000001)
            }
    }

    @Test
    fun `first private sample is zero even when the clock progresses on every read`() {
        setup().use { f ->
            val clock = AtomicLong(10_000_000_000)
            val personal =
                f.personal(
                    NpcPresentationOptions(
                        f.player,
                        clock = NpcMonotonicClock { clock.getAndAdd(10_000_000) },
                    )
                )
            drive(f.fixture, personal.publish())
            drive(f.fixture, personal.playClip("wave"))
            assertEquals(0.0, personal.snapshot().clipSeconds)
            f.fixture.instance.tick(0)
            assertEquals(0.01, personal.snapshot().clipSeconds, 0.000001)
        }
    }

    @Test
    fun `private movement close waits outstanding IO and retains its failure in parent drain`() {
        setup(gated = true).use { f ->
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            val instance = f.fixture.instance as GatedInstance
            instance.armed = true
            val move =
                f.parent
                    .applyTransform(NpcRenderTransform(NpcTransform(NpcVector3(10.0, 42.0, 0.0))))
                    .toCompletableFuture()
            until(f.fixture) { instance.waiting }
            val closing = personal.closeAsync().toCompletableFuture()
            try {
                assertFalse(closing.isDone, "A started private chunk load still belongs to cleanup")
                assertFalse(
                    move.isDone,
                    "Parent movement must not report success while private IO is outstanding",
                )
            } finally {
                instance.gate.completeExceptionally(
                    IllegalStateException("delayed private chunk failure")
                )
            }
            assertThrows(Exception::class.java) { closing.get(1, TimeUnit.SECONDS) }
            assertThrows(Exception::class.java) { drive(f.fixture, move) }
            assertThrows(Exception::class.java) {
                f.root.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            }
            assertTrue(f.parts().isEmpty())
        }
    }

    @Test
    fun `parent close drains a pending private load without needing a later owner tick`() {
        for (fail in listOf(false, true)) setup(gated = true).use { f ->
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            val instance = f.fixture.instance as GatedInstance
            instance.armed = true
            val move =
                f.parent
                    .applyTransform(NpcRenderTransform(NpcTransform(NpcVector3(10.0, 42.0, 0.0))))
                    .toCompletableFuture()
            until(f.fixture) { instance.waiting }
            val closing = f.root.cancel().toCompletableFuture()
            try {
                assertFalse(closing.isDone)
            } finally {
                if (fail)
                    instance.gate.completeExceptionally(
                        IllegalStateException("late owned IO failure")
                    )
                else instance.gate.complete(instance.getChunk(0, 0))
            }
            if (fail) assertThrows(Exception::class.java) { closing.get(1, TimeUnit.SECONDS) }
            else closing.get(1, TimeUnit.SECONDS)
            assertTrue(move.isCompletedExceptionally)
            assertTrue(f.parts().isEmpty())
            repeat(4) { instance.tick(0) }
            assertTrue(f.parts().isEmpty())
        }
    }

    @Test
    fun `a later teleport listener failure still drains an earlier accepted part load`() {
        setup(gated = true).use { f ->
            val common = f.parts().toSet()
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            val copies = f.parts().filter { it !in common }.sortedBy { it.entityId }
            MinecraftServer.getExceptionManager().setExceptionHandler { throw it }
            copies.last().eventNode().addListener(EntityTeleportEvent::class.java) {
                throw IllegalStateException("later part listener failed")
            }
            val instance = f.fixture.instance as GatedInstance
            instance.targetX = 32.0
            instance.gateAt = 5
            instance.armed = true
            val move =
                f.parent.applyTransform(
                    NpcRenderTransform(NpcTransform(NpcVector3(32.0, 42.0, 0.0)))
                )
            until(f.fixture) { instance.waiting }
            val closing = personal.closeAsync().toCompletableFuture()
            try {
                assertFalse(
                    closing.isDone,
                    "Earlier accepted part load must survive a later listener exception",
                )
            } finally {
                instance.gate.complete(instance.getChunk(2, 0))
            }
            assertThrows(Exception::class.java) { closing.get(1, TimeUnit.SECONDS) }
            assertThrows(Exception::class.java) { drive(f.fixture, move) }
            assertThrows(Exception::class.java) {
                f.root.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            }
            assertTrue(f.parts().isEmpty())
        }
    }

    @Test
    fun `teleport event and reentrant close observer run outside NPC monitor`() {
        setup().use { f ->
            val common = f.parts().toSet()
            val personal = f.personal()
            drive(f.fixture, personal.publish())
            val privateParts = f.parts().filter { it !in common }
            val field =
                NpcItemDisplay::class.java.getDeclaredField("lock").also { it.isAccessible = true }
            val monitor = field.get(privateParts.first())
            var eventHeld: Boolean? = null
            var observerHeld: Boolean? = null
            personal.closed().whenComplete { _, _ -> observerHeld = Thread.holdsLock(monitor) }
            privateParts.first().eventNode().addListener(EntityTeleportEvent::class.java) {
                eventHeld = Thread.holdsLock(monitor)
                personal.closeAsync()
            }
            val moving =
                f.parent.applyTransform(
                    NpcRenderTransform(NpcTransform(NpcVector3(10.0, 42.0, 0.0)))
                )
            until(f.fixture) { personal.closed().toCompletableFuture().isDone }
            assertEquals(false, eventHeld)
            assertEquals(false, observerHeld)
            assertEquals(2, f.parts().size)
        }
    }

    private class GatedInstance : InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD) {
        var armed = false
        var targetX = 10.0
        var gateAt = 2
        var waiting = false
        var loads = 0
        val gate = CompletableFuture<Chunk?>()

        override fun loadOptionalChunk(point: Point): CompletableFuture<Chunk?> {
            if (armed && point.x() == targetX && ++loads == gateAt) {
                waiting = true
                return gate
            }
            return super.loadOptionalChunk(point)
        }
    }

    private class Setup(
        val fixture: RendererFixture,
        val root: NpcStart,
        val parent: NpcHandle,
        val player: TestPlayer,
        val other: TestPlayer,
    ) : AutoCloseable {
        fun personal(options: NpcPresentationOptions = NpcPresentationOptions(player)) =
            (drive(fixture, NpcPresentations.prepare(parent, options).ready())
                    as NpcPresentationResult.Ready)
                .handle

        fun parts() = fixture.instance.entities.filterIsInstance<NpcItemDisplay>()

        override fun close() {
            root.cancel()
            fixture.close()
        }
    }

    private fun setup(
        limits: NpcPresentationLimits = NpcPresentationLimits(),
        config: NpcRenderConfig = NpcRenderConfig(),
        gated: Boolean = false,
    ): Setup {
        MinecraftServer.init()
        val fixture =
            if (gated) {
                val i = GatedInstance()
                MinecraftServer.getInstanceManager().registerInstance(i)
                RendererFixture(i)
            } else RendererFixture()
        val player = fixture.player(Pos(0.0, 42.0, 0.0))
        val other = fixture.player(Pos(1.0, 42.0, 0.0))
        val root =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    fixture.instance,
                    personalTestModel(true),
                    NpcLifecycleTest.transform(),
                    config = config,
                ),
                limits,
            )
        val parent = (drive(fixture, root.ready()) as NpcPrepareResult.Ready).handle
        drive(fixture, parent.publish())
        return Setup(fixture, root, parent, player, other)
    }

    private fun until(fixture: RendererFixture, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!predicate() && System.nanoTime() < deadline) {
            fixture.instance.tick(0)
            Thread.yield()
        }
        assertTrue(predicate(), "bounded owner ticks did not reach the requested condition")
    }
}
