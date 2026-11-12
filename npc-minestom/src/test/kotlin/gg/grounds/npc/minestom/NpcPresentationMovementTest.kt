package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import gg.grounds.npc.minestom.NpcPresentationLifecycleTest.Companion.await
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.*
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcPresentationMovementTest {
    @Test
    fun `parent movement and local scale reach every private part before completion`(env: Env) {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
            )
        val parent = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val clock = AtomicLong(10_000_000_000)
        val personalStart =
            NpcPresentations.prepare(
                parent,
                NpcPresentationOptions(player, clock = NpcMonotonicClock(clock::get)),
            )
        try {
            val personal = (await(env, personalStart.ready()) as NpcPresentationResult.Ready).handle
            await(env, personal.publish())
            await(env, personal.playClip("wave"))
            clock.set(10_250_000_000)
            env.tick()
            val target =
                NpcRenderTransform(
                    NpcTransform(NpcVector3(10.0, 42.0, 3.0)),
                    NpcTransform(NpcVector3.ZERO, scale = NpcVector3(2.0, 2.0, 2.0)),
                )
            await(env, parent.applyTransform(target))
            val parts = instance.entities.filterIsInstance<NpcItemDisplay>()
            assertEquals(4, parts.size)
            assertTrue(
                parts.all { it.position.x() == 10.0 },
                "No private group may remain at the old anchor",
            )
            assertTrue(
                parts.all {
                    kotlin.math.abs((it.entityMeta as ItemDisplayMeta).scale.x() - 2.0) < 0.00001
                }
            )
            assertEquals(NpcPresentationChannel.PRIVATE, personal.snapshot().clipMode)
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
        } finally {
            personalStart.cancel()
            start.cancel()
        }
    }

    @Test
    fun `movement also synchronizes a personal group that is still preparing`(env: Env) {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
            )
        val parent = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val personalStart = NpcPresentations.prepare(parent, NpcPresentationOptions(player))
        try {
            val target = NpcRenderTransform(NpcTransform(NpcVector3(12.0, 42.0, 3.0)))
            await(env, parent.applyTransform(target))
            val personal = (await(env, personalStart.ready()) as NpcPresentationResult.Ready).handle
            await(env, personal.publish())
            assertTrue(
                instance.entities.filterIsInstance<NpcItemDisplay>().all { it.position.x() == 12.0 }
            )
        } finally {
            personalStart.cancel()
            start.cancel()
        }
    }

    @Test
    fun `private close during delayed parent chunk loading cannot resurrect old parts`(env: Env) {
        val instance = env.createFlatInstance() as InstanceContainer
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
            )
        val parent = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val common = instance.entities.filterIsInstance<NpcItemDisplay>().toSet()
        val personalStart = NpcPresentations.prepare(parent, NpcPresentationOptions(player))
        val personal = (await(env, personalStart.ready()) as NpcPresentationResult.Ready).handle
        await(env, personal.publish())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        instance.setChunkLoader(
            object : ChunkLoader {
                override fun supportsParallelLoading() = true

                override fun loadChunk(instance: Instance, x: Int, z: Int): Chunk? {
                    entered.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    return null
                }

                override fun saveChunk(chunk: Chunk) {}
            }
        )
        try {
            val move =
                parent.applyTransform(
                    NpcRenderTransform(NpcTransform(NpcVector3(512.0, 42.0, 512.0)))
                )
            env.tick()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            personalStart.cancel().toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(common, instance.entities.filterIsInstance<NpcItemDisplay>().toSet())
            release.countDown()
            await(env, move)
            repeat(4) { env.tick() }
            assertEquals(common, instance.entities.filterIsInstance<NpcItemDisplay>().toSet())
            assertTrue(common.all { it.position.x() == 512.0 })
        } finally {
            release.countDown()
            personalStart.cancel()
            start.cancel()
        }
    }
}
