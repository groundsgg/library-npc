package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.NpcPresentationLifecycleTest.Companion.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcPresentationSoundTest {
    @Test
    fun `private sounds target independent owners once and never use the shared spatial sink`(
        env: Env
    ) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val b = env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        env.createPlayer(instance, Pos(2.0, 42.0, 0.0))
        val sharedSounds = CopyOnWriteArrayList<String>()
        val personalSounds = CopyOnWriteArrayList<Pair<Player, String>>()
        val parentStart =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    personalTestModel(true),
                    NpcLifecycleTest.transform(),
                    sounds = NpcSoundSink { _, _, sound -> sharedSounds += sound.key },
                )
            )
        val parent = (await(env, parentStart.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val clock = AtomicLong(10_000_000_000)
        val sink = NpcViewerSoundSink { player, _, sound -> personalSounds += player to sound.key }
        val sa =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(
                    a,
                    sounds = sink,
                    idleClip = "idle",
                    clock = NpcMonotonicClock(clock::get),
                ),
            )
        val sb =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(
                    b,
                    sounds = sink,
                    idleClip = "idle",
                    clock = NpcMonotonicClock(clock::get),
                ),
            )
        try {
            val ha = (await(env, sa.ready()) as NpcPresentationResult.Ready).handle
            val hb = (await(env, sb.ready()) as NpcPresentationResult.Ready).handle
            await(env, ha.publish())
            await(env, hb.publish())
            await(env, ha.playClip("wave"))
            await(env, hb.playClip("loop"))
            assertEquals(listOf(a to "demo:wave", b to "demo:loop"), personalSounds.toList())
            assertTrue(sharedSounds.isEmpty())
            clock.set(12_500_000_000)
            repeat(4) { env.tick() }
            ha.snapshot()
            hb.snapshot()
            await(env, ha.applyHeadLook(20.0, 10.0))
            await(env, ha.followSharedHeadLook())
            assertEquals(2, personalSounds.size)
            await(env, parent.startAnimation("wave", 0))
            assertEquals(listOf("demo:wave"), sharedSounds.toList())
            assertEquals(2, personalSounds.size)
            await(env, ha.playClip("wave"))
            assertEquals(
                listOf(a to "demo:wave", b to "demo:loop", a to "demo:wave"),
                personalSounds.toList(),
            )
        } finally {
            sa.cancel()
            sb.cancel()
            parentStart.cancel()
        }
    }

    @Test
    fun `cancelled queued private cues cannot deliver a sound`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        var sounds = 0
        val parentStart =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(true), NpcLifecycleTest.transform())
            )
        val parent = (await(env, parentStart.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val start =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(a, sounds = NpcViewerSoundSink { _, _, _ -> sounds++ }),
            )
        try {
            val personal = (await(env, start.ready()) as NpcPresentationResult.Ready).handle
            await(env, personal.publish())
            val queued = personal.playClip("wave").toCompletableFuture()
            start.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            repeat(4) { env.tick() }
            assertTrue(queued.isCompletedExceptionally)
            assertEquals(0, sounds)
        } finally {
            start.cancel()
            parentStart.cancel()
        }
    }

    @Test
    fun `close returns without the sound callback lock but drains its accepted delivery`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val hostLock = ReentrantLock()
        val entered = CountDownLatch(1)
        val parentStart =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(true), NpcLifecycleTest.transform())
            )
        val parent = (await(env, parentStart.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val start =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(
                    a,
                    sounds =
                        NpcViewerSoundSink { _, _, _ ->
                            entered.countDown()
                            hostLock.lock()
                            try {
                                /* synchronous accepted delivery */
                            } finally {
                                hostLock.unlock()
                            }
                        },
                ),
            )
        val personal = (await(env, start.ready()) as NpcPresentationResult.Ready).handle
        await(env, personal.publish())
        hostLock.lock()
        var cancelling: CompletableFuture<java.util.concurrent.CompletionStage<Void>>? = null
        personal.playClip("wave")
        val ticking = CompletableFuture.runAsync { env.tick() }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS), "Private sound cue must be delivered")
            cancelling = CompletableFuture.supplyAsync { start.cancel() }
            val cleanup = cancelling.get(500, TimeUnit.MILLISECONDS)
            assertFalse(
                cleanup.toCompletableFuture().isDone,
                "Close owns delivery accepted before cancellation",
            )
        } finally {
            hostLock.unlock()
            ticking.get(3, TimeUnit.SECONDS)
            cancelling?.get(3, TimeUnit.SECONDS)?.toCompletableFuture()?.get(3, TimeUnit.SECONDS)
            start.cancel()
            parentStart.cancel()
        }
    }

    @Test
    fun `a failing playback clock closes only its private group and exposes the failure`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val b = env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val parentStart =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
            )
        val parent = (await(env, parentStart.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val common = instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
        var failing = false
        val start =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(
                    a,
                    clock =
                        NpcMonotonicClock {
                            check(!failing) { "original synthetic clock failure" }
                            10_000_000_000
                        },
                ),
            )
        val personal = (await(env, start.ready()) as NpcPresentationResult.Ready).handle
        await(env, personal.publish())
        await(env, personal.playClip("wave"))
        failing = true
        env.tick()
        assertTrue(personal.closed().toCompletableFuture().isCompletedExceptionally)
        assertEquals(common, instance.entities.filterIsInstance<NpcItemDisplay>().toSet())
        assertTrue(common.all { a in it.viewers && b in it.viewers })
        val closedParent = parentStart.cancel().toCompletableFuture()
        assertTrue(
            closedParent.isCompletedExceptionally,
            "Parent drain must retain previously failed owned work",
        )
        assertTrue(instance.entities.filterIsInstance<NpcItemDisplay>().isEmpty())
    }
}
