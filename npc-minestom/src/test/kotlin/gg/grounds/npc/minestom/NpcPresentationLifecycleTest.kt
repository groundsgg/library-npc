package gg.grounds.npc.minestom

import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcPresentationLifecycleTest {
    @Test
    fun `Java consumer privately publishes only after hidden readiness and restores common on close`(
        env: Env
    ) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val b = env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val parent = parent(env, instance)
        val common = displays(instance)
        val start = JavaPresentationConsumer.prepare(parent, a)
        try {
            val ready = await(env, start.ready()) as NpcPresentationResult.Ready
            val personal = ready.handle
            val copied = displays(instance) - common.toSet()
            assertEquals(2, copied.size)
            assertTrue(common.all { a in it.viewers && b in it.viewers })
            assertTrue(copied.all { it.viewers.isEmpty() })
            assertTrue(personal.playClip("wave").toCompletableFuture().isCompletedExceptionally)
            await(env, personal.publish())
            assertEquals(NpcPresentationPhase.ACTIVE, personal.snapshot().phase)
            assertTrue(common.all { a !in it.viewers && b in it.viewers })
            assertTrue(copied.all { a in it.viewers && b !in it.viewers })
            await(env, personal.publish())
            assertEquals(4, displays(instance).size)
            personal.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(NpcPresentationPhase.CLOSED, personal.snapshot().phase)
            assertEquals(common.toSet(), displays(instance).toSet())
            assertTrue(common.all { a in it.viewers && b in it.viewers })
            personal.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
        } finally {
            start.cancel()
            parent.closeAsync()
        }
    }

    @Test
    fun `pending starts are reserved and parent close needs no next tick`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val parent = parent(env, instance)
        val first = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        val second = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        assertInstanceOf(
            NpcPresentationResult.Rejected::class.java,
            second.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
        )
        parent.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
        first.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
        assertInstanceOf(
            NpcPresentationResult.Rejected::class.java,
            first.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
        )
        repeat(3) { env.tick() }
        assertTrue(displays(instance).isEmpty())
    }

    @Test
    fun `cancelled queued presentation cannot hide a successor`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val parent = parent(env, instance)
        val common = displays(instance)
        val cancelled = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        cancelled.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
        val replacement = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        try {
            val personal = (await(env, replacement.ready()) as NpcPresentationResult.Ready).handle
            await(env, personal.publish())
            repeat(4) { env.tick() }
            assertTrue(common.all { a !in it.viewers })
            assertTrue((displays(instance) - common.toSet()).all { a in it.viewers })
        } finally {
            replacement.cancel()
            parent.closeAsync()
        }
    }

    @Test
    fun `capacity rejection leaves common visible and allocates no private displays`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val b = env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val parent = parent(env, instance, NpcPresentationLimits(1, 2))
        val first = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        val rejected = NpcPresentations.prepare(parent, NpcPresentationOptions(b))
        assertInstanceOf(
            NpcPresentationResult.Rejected::class.java,
            rejected.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
        )
        assertEquals(2, displays(instance).size)
        assertTrue(displays(instance).all { a in it.viewers && b in it.viewers })
        first.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
        parent.closeAsync()
        val partsLimited = parent(env, instance, NpcPresentationLimits(32, 1))
        try {
            val result =
                NpcPresentations.prepare(partsLimited, NpcPresentationOptions(a))
                    .ready()
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)
            assertInstanceOf(NpcPresentationResult.Rejected::class.java, result)
            assertEquals(2, displays(instance).size)
            assertTrue(displays(instance).all { a in it.viewers })
        } finally {
            partsLimited.closeAsync()
        }
    }

    @Test
    fun `foreign closed and mismatched-instance parents are rejected`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val parent = parent(env, instance)
        val foreign = object : NpcHandle by parent {}
        val other = env.createPlayer(env.createFlatInstance(), Pos(0.0, 42.0, 0.0))
        for ((handle, player) in listOf(foreign to a, parent to other)) {
            assertInstanceOf(
                NpcPresentationResult.Rejected::class.java,
                NpcPresentations.prepare(handle, NpcPresentationOptions(player))
                    .ready()
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS),
            )
        }
        parent.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
        assertInstanceOf(
            NpcPresentationResult.Rejected::class.java,
            NpcPresentations.prepare(parent, NpcPresentationOptions(a))
                .ready()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS),
        )
    }

    @Test
    fun `closing the only personal viewer restores the current shared head instead of stale metadata`(
        env: Env
    ) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val parent = parent(env, instance)
        val common = displays(instance).sortedBy { it.entityId }
        val original =
            (common[1].entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta)
                .translation
        val start = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        try {
            val handle = (await(env, start.ready()) as NpcPresentationResult.Ready).handle
            await(env, handle.publish())
            await(env, parent.applyHeadLook(40.0, -20.0))
            handle.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertTrue(common.all { a in it.viewers })
            assertNotEquals(
                original,
                (common[1].entityMeta
                        as net.minestom.server.entity.metadata.display.ItemDisplayMeta)
                    .translation,
                "Restoring the common renderer must first refresh its actual pose",
            )
        } finally {
            start.cancel()
            parent.closeAsync()
        }
    }

    @Test
    fun `default capacity admits 32 pending owners and rejects the 33rd before hiding`(env: Env) {
        val instance = env.createFlatInstance()
        val players = (0..32).map { env.createPlayer(instance, Pos(it / 32.0, 42.0, 0.0)) }
        val parent = parent(env, instance)
        val starts =
            players.take(32).map { NpcPresentations.prepare(parent, NpcPresentationOptions(it)) }
        try {
            assertTrue(starts.all { !it.ready().toCompletableFuture().isDone })
            val refused = NpcPresentations.prepare(parent, NpcPresentationOptions(players.last()))
            assertInstanceOf(
                NpcPresentationResult.Rejected::class.java,
                refused.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
            )
            assertEquals(2, displays(instance).size)
            assertTrue(displays(instance).all { display -> players.all { it in display.viewers } })
        } finally {
            parent.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `default part budget admits twenty 50-part presentations and rejects the next`(env: Env) {
        val instance = env.createFlatInstance()
        val players = (0..20).map { env.createPlayer(instance, Pos(it / 20.0, 42.0, 0.0)) }
        val base = NpcLifecycleTest.model()
        val model =
            gg.grounds.npc.core.NpcModel(
                base.id,
                base.bounds,
                base.bones,
                List(50) { base.parts[0] },
                base.animations,
                base.sounds,
            )
        val parent = parent(env, instance, model = model)
        val starts =
            players.take(20).map { NpcPresentations.prepare(parent, NpcPresentationOptions(it)) }
        try {
            assertTrue(starts.all { !it.ready().toCompletableFuture().isDone })
            val refused = NpcPresentations.prepare(parent, NpcPresentationOptions(players.last()))
            assertInstanceOf(
                NpcPresentationResult.Rejected::class.java,
                refused.ready().toCompletableFuture().get(1, TimeUnit.SECONDS),
            )
            assertEquals(50, displays(instance).size)
            assertTrue(displays(instance).all { display -> players.all { it in display.viewers } })
        } finally {
            parent.closeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `cancelling before handover never enters a foreign parent publication guard`(env: Env) {
        val instance = env.createFlatInstance()
        val a = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        var rejectGuard = false
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    NpcLifecycleTest.model(),
                    NpcLifecycleTest.transform(),
                    publicationGuard =
                        NpcPublicationGuard { operation ->
                            check(!rejectGuard) {
                                "An unleased cancellation must not reenter the publication guard"
                            }
                            operation.run()
                            true
                        },
                )
            )
        val parent = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val personal = NpcPresentations.prepare(parent, NpcPresentationOptions(a))
        rejectGuard = true
        try {
            personal.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(2, displays(instance).size)
            assertTrue(displays(instance).all { a in it.viewers })
        } finally {
            parent.closeAsync()
        }
    }

    companion object {
        fun <T> await(env: Env, stage: CompletionStage<T>): T {
            val future = stage.toCompletableFuture()
            assertTrue(
                env.tickWhile({ !future.isDone }, Duration.ofSeconds(3)),
                "NPC owner continuation timed out",
            )
            return future.get(1, TimeUnit.SECONDS)
        }

        internal fun displays(instance: Instance) =
            instance.entities.filterIsInstance<NpcItemDisplay>()

        fun parent(
            env: Env,
            instance: Instance,
            limits: NpcPresentationLimits = NpcPresentationLimits(),
            model: gg.grounds.npc.core.NpcModel = NpcLifecycleTest.model(),
        ): NpcHandle {
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(instance, model, NpcLifecycleTest.transform()),
                    limits,
                )
            val handle = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
            await(env, handle.publish())
            return handle
        }
    }
}
