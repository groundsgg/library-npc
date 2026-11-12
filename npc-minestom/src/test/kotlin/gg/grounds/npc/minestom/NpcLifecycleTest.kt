package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.time.Duration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcLifecycleTest {
    @Test
    fun `host invalidation cannot deadlock with a pending publication guard`(env: Env) {
        val instance = env.createFlatInstance()
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val hostLock = java.util.concurrent.locks.ReentrantLock()
        val entered = java.util.concurrent.CountDownLatch(1)
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    model(),
                    transform(),
                    publicationGuard =
                        NpcPublicationGuard { operation ->
                            entered.countDown()
                            hostLock.lock()
                            try {
                                operation.run()
                                true
                            } finally {
                                hostLock.unlock()
                            }
                        },
                )
            )
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        hostLock.lock()
        var invalidationReturned = false
        val publishing = handle.publish().toCompletableFuture()
        val ticking = java.util.concurrent.CompletableFuture.runAsync { env.tick() }
        var cancelling:
            java.util.concurrent.CompletableFuture<java.util.concurrent.CompletionStage<Void>>? =
            null
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            cancelling = java.util.concurrent.CompletableFuture.supplyAsync { start.cancel() }
            try {
                cancelling.get(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                invalidationReturned = true
            } catch (_: java.util.concurrent.TimeoutException) {}
        } finally {
            hostLock.unlock()
        }
        ticking.get(3, java.util.concurrent.TimeUnit.SECONDS)
        cancelling!!
            .get(3, java.util.concurrent.TimeUnit.SECONDS)
            .toCompletableFuture()
            .get(3, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(invalidationReturned, "Host guard must run outside the NPC ownership lock")
        assertTrue(publishing.isDone)
    }

    @Test
    fun `move completion waits for chunk loading and actual relocation`(env: Env) {
        val ownerThreads = java.util.concurrent.ConcurrentHashMap.newKeySet<Thread>()
        val ownerMarkers = mutableListOf<net.minestom.server.timer.Task>()
        val teleportThreads = java.util.concurrent.ConcurrentLinkedQueue<Thread>()
        val instance = env.createFlatInstance() as net.minestom.server.instance.InstanceContainer
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        instance.entities
            .filter { it.entityType == EntityType.ITEM_DISPLAY }
            .forEach { part ->
                ownerMarkers +=
                    part
                        .scheduler()
                        .buildTask { ownerThreads += Thread.currentThread() }
                        .delay(net.minestom.server.timer.TaskSchedule.tick(1))
                        .repeat(net.minestom.server.timer.TaskSchedule.tick(1))
                        .schedule()
                part.eventNode().addListener(
                    net.minestom.server.event.entity.EntityTeleportEvent::class.java
                ) {
                    teleportThreads += Thread.currentThread()
                }
            }

        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        instance.setChunkLoader(
            object : net.minestom.server.instance.ChunkLoader {
                override fun supportsParallelLoading() = true

                override fun loadChunk(
                    instance: net.minestom.server.instance.Instance,
                    x: Int,
                    z: Int,
                ): net.minestom.server.instance.Chunk? {
                    entered.countDown()
                    check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return null
                }

                override fun saveChunk(chunk: net.minestom.server.instance.Chunk) {}
            }
        )
        try {
            val target = NpcVector3(512.0, 42.0, 512.0)
            ownerMarkers +=
                instance
                    .scheduler()
                    .buildTask { ownerThreads += Thread.currentThread() }
                    .delay(net.minestom.server.timer.TaskSchedule.tick(1))
                    .repeat(net.minestom.server.timer.TaskSchedule.tick(1))
                    .schedule()
            val moving =
                handle
                    .applyTransform(NpcRenderTransform(NpcTransform(target)))
                    .toCompletableFuture()
            env.tick()
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(
                moving.isDone,
                "Move future must not succeed while its destination is still loading",
            )
            release.countDown()
            assertTrue(env.tickWhile({ !moving.isDone }, wait))
            moving.join()
            assertTrue(teleportThreads.isNotEmpty())
            assertTrue(
                teleportThreads.all { it in ownerThreads },
                "Chunk completion must use an instance/entity owner ${ownerThreads.map { it.name }}; actual ${teleportThreads.map { it.name }}",
            )
            assertEquals(target, handle.snapshot().root.position)
            assertTrue(
                instance.entities
                    .filter { it.entityType == EntityType.ITEM_DISPLAY }
                    .all { it.position.x() == target.x }
            )
        } finally {
            ownerMarkers.forEach { it.cancel() }
            release.countDown()
            handle.closeAsync()
        }
    }

    @Test
    fun `closing does not depend on another entity or instance scheduler tick`(env: Env) {
        val instance = env.createFlatInstance()
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        val queued = handle.publish().toCompletableFuture()
        assertFalse(queued.isDone)
        // Simulate a stopped tick driver: never tick after requesting this queued command.
        start.cancel().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(queued.isCompletedExceptionally)
        assertTrue(instance.entities.none { it.entityType == EntityType.ITEM_DISPLAY })
    }

    @Test
    fun `mesh resolution failure closes every allocated part and reports rejection`(env: Env) {
        val instance = env.createFlatInstance()
        var count = 0
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    model(),
                    transform(),
                    meshes =
                        NpcMeshResolver {
                            if (++count == 2) throw IllegalStateException("test mesh unavailable")
                            net.minestom.server.item.ItemStack.of(
                                net.minestom.server.item.Material.PAPER
                            )
                        },
                )
            )
        assertInstanceOf(
            NpcPrepareResult.Rejected::class.java,
            start.ready().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS),
        )
        start.cancel().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
        repeat(4) { env.tick() }
        assertTrue(instance.entities.none { it.entityType == EntityType.ITEM_DISPLAY })
    }

    @Test
    fun `cancelled part attachment rejects preparation without publishing a sibling`(env: Env) {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        var attachments = 0
        instance.eventNode().addListener(
            net.minestom.server.event.instance.AddEntityToInstanceEvent::class.java
        ) {
            if (it.entity.entityType == EntityType.ITEM_DISPLAY && ++attachments == 2)
                it.isCancelled = true
        }
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        assertInstanceOf(NpcPrepareResult.Rejected::class.java, ready.join())
        start.cancel().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(instance.entities.none { it.entityType == EntityType.ITEM_DISPLAY })
    }

    @Test
    fun `injected sound sink cannot hold the NPC ownership lock during cancellation`(env: Env) {
        val instance = env.createFlatInstance()
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val hostLock = java.util.concurrent.locks.ReentrantLock()
        val entered = java.util.concurrent.CountDownLatch(1)
        val base = model()
        val voiced =
            NpcModel(
                base.id,
                base.bounds,
                base.bones,
                base.parts,
                mapOf("wave" to NpcClip("once", 1.0, emptyMap())),
                mapOf("wave" to NpcClipSound("demo:wave", 1.0, 1.0, 12.0)),
            )
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    voiced,
                    transform(),
                    sounds =
                        NpcSoundSink { _, _, _ ->
                            entered.countDown()
                            hostLock.lock()
                            try {} finally {
                                hostLock.unlock()
                            }
                        },
                )
            )
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        hostLock.lock()
        handle.startAnimation("wave", 0)
        val ticking = java.util.concurrent.CompletableFuture.runAsync { env.tick() }
        var returned = false
        var cancelling:
            java.util.concurrent.CompletableFuture<java.util.concurrent.CompletionStage<Void>>? =
            null
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            cancelling = java.util.concurrent.CompletableFuture.supplyAsync { start.cancel() }
            try {
                cancelling.get(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                returned = true
            } catch (_: java.util.concurrent.TimeoutException) {}
        } finally {
            hostLock.unlock()
        }
        ticking.get(3, java.util.concurrent.TimeUnit.SECONDS)
        cancelling!!
            .get(3, java.util.concurrent.TimeUnit.SECONDS)
            .toCompletableFuture()
            .get(3, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(returned, "Host sound callback must run outside the NPC ownership lock")
    }

    @Test
    fun `loaded movement continuation can be cancelled without another owner tick`(env: Env) {
        val instance = env.createFlatInstance() as net.minestom.server.instance.InstanceContainer
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val loaded = java.util.concurrent.CountDownLatch(1)
        instance.eventNode().addListener(
            net.minestom.server.event.instance.InstanceChunkLoadEvent::class.java
        ) {
            if (it.chunk.chunkX == 32) loaded.countDown()
        }
        instance.setChunkLoader(
            object : net.minestom.server.instance.ChunkLoader {
                override fun supportsParallelLoading() = true

                override fun loadChunk(
                    instance: net.minestom.server.instance.Instance,
                    x: Int,
                    z: Int,
                ): net.minestom.server.instance.Chunk? {
                    entered.countDown()
                    check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return null
                }

                override fun saveChunk(chunk: net.minestom.server.instance.Chunk) {}
            }
        )
        try {
            val moving =
                handle
                    .applyTransform(
                        NpcRenderTransform(NpcTransform(NpcVector3(512.0, 42.0, 512.0)))
                    )
                    .toCompletableFuture()
            env.tick()
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            release.countDown()
            assertTrue(loaded.await(3, java.util.concurrent.TimeUnit.SECONDS))
            // Destination is loaded, but the queued owner continuation has never run.
            assertFalse(moving.isDone)
            start.cancel().toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(moving.isCompletedExceptionally)
            repeat(8) { env.tick() }
            assertTrue(instance.entities.none { it.entityType == EntityType.ITEM_DISPLAY })
        } finally {
            release.countDown()
            start.cancel()
        }
    }

    private val wait = Duration.ofSeconds(3)

    @Test
    fun `prepared parts stay hidden until publication and close removes every part`(env: Env) {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        assertEquals(2, instance.entities.filter { it.entityType == EntityType.ITEM_DISPLAY }.size)
        assertTrue(
            instance.entities
                .filter { it.entityType == EntityType.ITEM_DISPLAY }
                .all { it.viewers.isEmpty() && !it.hasPhysics() }
        )
        val published = handle.publish().toCompletableFuture()
        assertTrue(
            env.tickWhile(
                {
                    !published.isDone ||
                        instance.entities
                            .filter { it.entityType == EntityType.ITEM_DISPLAY }
                            .any { player !in it.viewers }
                },
                wait,
            )
        )
        val closing = handle.closeAsync().toCompletableFuture()
        assertTrue(env.tickWhile({ !closing.isDone }, wait))
        assertTrue(instance.entities.filter { it.entityType == EntityType.ITEM_DISPLAY }.isEmpty())
        handle.closeAsync().toCompletableFuture().join()
    }

    @Test
    fun `cancellation before ready drains pending attachments without a late spawn`(env: Env) {
        val instance = env.createFlatInstance() as net.minestom.server.instance.InstanceContainer
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        instance.setChunkLoader(
            object : net.minestom.server.instance.ChunkLoader {
                override fun supportsParallelLoading() = true

                override fun loadChunk(
                    instance: net.minestom.server.instance.Instance,
                    x: Int,
                    z: Int,
                ): net.minestom.server.instance.Chunk? {
                    entered.countDown()
                    check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return null
                }

                override fun saveChunk(chunk: net.minestom.server.instance.Chunk) {}
            }
        )
        try {
            val start =
                NpcRenderer.prepare(
                    NpcSpawnRequest(
                        instance,
                        model(),
                        NpcRenderTransform(NpcTransform(NpcVector3(512.0, 42.0, 512.0))),
                    )
                )
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            val closing = start.cancel().toCompletableFuture()
            assertFalse(closing.isDone, "Cleanup must await the pending attachment")
            release.countDown()
            assertTrue(env.tickWhile({ !closing.isDone }, wait))
            assertInstanceOf(
                NpcPrepareResult.Rejected::class.java,
                start.ready().toCompletableFuture().join(),
            )
            repeat(8) { env.tick() }
            assertTrue(
                instance.entities.filter { it.entityType == EntityType.ITEM_DISPLAY }.isEmpty()
            )
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `invalidated host guard prevents publication and disposes ready parts`(env: Env) {
        val instance = env.createFlatInstance()
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(
                    instance,
                    model(),
                    transform(),
                    publicationGuard = NpcPublicationGuard { false },
                )
            )
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        val publishing = handle.publish().toCompletableFuture()
        assertTrue(env.tickWhile({ !publishing.isDone }, wait))
        assertTrue(publishing.isCompletedExceptionally)
        val closing = handle.closeAsync().toCompletableFuture()
        assertTrue(env.tickWhile({ !closing.isDone }, wait))
        assertTrue(instance.entities.filter { it.entityType == EntityType.ITEM_DISPLAY }.isEmpty())
    }

    @Test
    fun `animation and additive head look preserve the independent body pose`(env: Env) {
        val instance = env.createFlatInstance()
        env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start = NpcRenderer.prepare(NpcSpawnRequest(instance, model(), transform()))
        val ready = start.ready().toCompletableFuture()
        assertTrue(env.tickWhile({ !ready.isDone }, wait))
        val handle = (ready.join() as NpcPrepareResult.Ready).handle
        val published = handle.publish().toCompletableFuture()
        assertTrue(env.tickWhile({ !published.isDone }, wait))
        val parts =
            instance.entities
                .filter { it.entityType == EntityType.ITEM_DISPLAY }
                .sortedBy { it.entityId }
        val body = parts[0].entityMeta as ItemDisplayMeta
        val head = parts[1].entityMeta as ItemDisplayMeta
        val originalBody = body.translation
        val originalHead = head.translation
        val command = handle.applyHeadLook(35.0, -25.0).toCompletableFuture()
        assertTrue(env.tickWhile({ !command.isDone }, wait))
        assertEquals(originalBody, body.translation)
        assertNotEquals(originalHead, head.translation)
        val closing = handle.closeAsync().toCompletableFuture()
        assertTrue(env.tickWhile({ !closing.isDone }, wait))
    }

    companion object {
        fun transform() = NpcRenderTransform(NpcTransform(NpcVector3(0.0, 42.0, 3.0)))

        fun model() =
            NpcModel(
                "demo:headed",
                NpcBounds(NpcVector3(0.0, 1.0, 0.0), NpcVector3(1.0, 2.0, 1.0)),
                listOf(
                    NpcBone("body", null, ZERO, ZERO),
                    NpcBone("head", "body", NpcVector3(0.0, 1.5, 0.0), ZERO),
                ),
                listOf(
                    NpcPart("body", "minecraft:stone", NpcVector3(0.0, 0.7, 0.0), ZERO, ZERO),
                    NpcPart(
                        "head",
                        "minecraft:stone",
                        NpcVector3(0.0, 1.8, -0.1),
                        NpcVector3(0.0, 1.5, 0.0),
                        ZERO,
                    ),
                ),
                emptyMap(),
                emptyMap(),
            )
    }
}
