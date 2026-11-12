package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import gg.grounds.npc.minestom.NpcPresentationLifecycleTest.Companion.await
import java.util.concurrent.atomic.AtomicLong
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal fun personalTestModel(voiced: Boolean = false): NpcModel {
    val base = NpcLifecycleTest.model()
    val moving =
        mapOf(
            "body" to
                mapOf(
                    "position" to
                        listOf(
                            NpcKeyframe(0.0, NpcVector3.ZERO, "linear"),
                            NpcKeyframe(1.0, NpcVector3(0.0, 1.0, 0.0), "linear"),
                        )
                )
        )
    return NpcModel(
        base.id,
        base.bounds,
        base.bones,
        base.parts,
        mapOf(
            "wave" to NpcClip("once", 1.0, moving),
            "idle" to NpcClip("loop", 2.0, emptyMap()),
            "loop" to NpcClip("loop", 1.0, moving),
        ),
        if (voiced)
            mapOf(
                "wave" to NpcClipSound("demo:wave", 1.0, 1.0, 12.0),
                "loop" to NpcClipSound("demo:loop", 1.0, 1.0, 12.0),
            )
        else emptyMap(),
    )
}

@EnvTest
class NpcPresentationPlaybackTest {
    @Test
    fun `private head follows real shared seek and pause until clip override`(env: Env) {
        val fixture = setup(env)
        try {
            await(env, fixture.parent.startAnimation("wave", 250))
            val personal = fixture.open()
            await(env, personal.applyHeadLook(35.0, -10.0))
            assertEquals(NpcPresentationChannel.PRIVATE, personal.snapshot().headMode)
            assertEquals(NpcPresentationChannel.FOLLOW_SHARED, personal.snapshot().clipMode)
            await(env, fixture.parent.advanceAnimation(100))
            fixture.clock.set(10_600_000_000)
            env.tick()
            assertEquals(0.1, personal.snapshot().clipSeconds, 0.000001)
            repeat(3) { env.tick() }
            assertEquals(0.1, personal.snapshot().clipSeconds, 0.000001)
            await(env, fixture.parent.applyHeadLook(-20.0, 10.0))
            await(env, fixture.parent.advanceAnimation(400))
            env.tick()
            val privateHead = fixture.privateParts()[1].entityMeta as ItemDisplayMeta
            val commonHead = fixture.common[1].entityMeta as ItemDisplayMeta
            assertNotEquals(commonHead.translation, privateHead.translation)
            assertEquals(0.4, personal.snapshot().clipSeconds, 0.000001)
            await(env, personal.followSharedHeadLook())
            assertEquals(commonHead.translation, privateHead.translation)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `private clip starts at zero after uptime and keeps head following independently`(
        env: Env
    ) {
        val fixture = setup(env)
        try {
            val personal = fixture.open()
            await(env, personal.playClip("wave"))
            assertEquals(0.0, personal.snapshot().clipSeconds)
            fixture.clock.set(10_250_000_000)
            env.tick()
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
            val privateBody = fixture.privateParts()[0].entityMeta as ItemDisplayMeta
            assertEquals(0.95, privateBody.translation.y(), 0.00001)
            await(env, fixture.parent.applyHeadLook(40.0, -20.0))
            env.tick()
            assertEquals(NpcPresentationChannel.FOLLOW_SHARED, personal.snapshot().headMode)
            assertEquals(NpcPresentationChannel.PRIVATE, personal.snapshot().clipMode)
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
            val commonHead = fixture.common[1].entityMeta as ItemDisplayMeta
            val privateHead = fixture.privateParts()[1].entityMeta as ItemDisplayMeta
            assertEquals(commonHead.translation.x(), privateHead.translation.x(), 0.00001)
            assertEquals(commonHead.translation.y() + 0.25, privateHead.translation.y(), 0.00001)
            await(env, personal.followSharedClip())
            assertNull(personal.snapshot().clipId)
            assertEquals(0.7, privateBody.translation.y(), 0.00001)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `once and stop select private idle without returning to shared animation`(env: Env) {
        val fixture = setup(env)
        try {
            val personal = fixture.open()
            await(env, personal.playClip("wave"))
            fixture.clock.set(11_250_000_000)
            env.tick()
            assertEquals("idle", personal.snapshot().clipId)
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
            assertEquals(NpcPresentationChannel.PRIVATE, personal.snapshot().clipMode)
            await(env, fixture.parent.startAnimation("wave", 500))
            env.tick()
            assertEquals("idle", personal.snapshot().clipId)
            await(env, personal.playClip("loop"))
            fixture.clock.set(12_750_000_000)
            env.tick()
            assertEquals("loop", personal.snapshot().clipId)
            assertEquals(1.5, personal.snapshot().clipSeconds, 0.000001)
            await(env, personal.stopClip())
            assertEquals("idle", personal.snapshot().clipId)
            assertEquals(0.0, personal.snapshot().clipSeconds)
            fixture.clock.set(13_250_000_000)
            env.tick()
            assertEquals(0.5, personal.snapshot().clipSeconds, 0.000001)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a regressing injected clock cannot rewind a private clip`(env: Env) {
        val fixture = setup(env)
        try {
            val personal = fixture.open()
            await(env, personal.playClip("wave"))
            fixture.clock.set(10_250_000_000)
            env.tick()
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
            fixture.clock.set(10_100_000_000)
            env.tick()
            assertEquals(0.25, personal.snapshot().clipSeconds, 0.000001)
            fixture.clock.set(10_400_000_000)
            env.tick()
            assertEquals(0.4, personal.snapshot().clipSeconds, 0.000001)
            await(env, personal.playClip("wave"))
            assertEquals(0.0, personal.snapshot().clipSeconds)
        } finally {
            fixture.close()
        }
    }

    private fun setup(env: Env): PlaybackFixture {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest(instance, personalTestModel(), NpcLifecycleTest.transform())
            )
        val parent = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, parent.publish())
        val common = instance.entities.filterIsInstance<NpcItemDisplay>().sortedBy { it.entityId }
        val clock = AtomicLong(10_000_000_000)
        return PlaybackFixture(env, instance, player, parent, common, clock)
    }

    private class PlaybackFixture(
        val env: Env,
        val instance: net.minestom.server.instance.Instance,
        val player: net.minestom.server.entity.Player,
        val parent: NpcHandle,
        val common: List<NpcItemDisplay>,
        val clock: AtomicLong,
    ) {
        private var start: NpcPresentationStart? = null

        fun open(): NpcPresentationHandle {
            start =
                NpcPresentations.prepare(
                    parent,
                    NpcPresentationOptions(
                        player,
                        idleClip = "idle",
                        clock = NpcMonotonicClock(clock::get),
                    ),
                )
            val handle = (await(env, start!!.ready()) as NpcPresentationResult.Ready).handle
            await(env, handle.publish())
            return handle
        }

        fun privateParts() =
            instance.entities
                .filterIsInstance<NpcItemDisplay>()
                .filter { it !in common }
                .sortedBy { it.entityId }

        fun close() {
            start?.cancel()
            parent.closeAsync()
        }
    }
}
