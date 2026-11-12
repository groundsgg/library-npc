package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.time.Duration
import java.util.concurrent.CompletableFuture
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcPresentationFrameTest {
    @Test
    fun `personal audience never admits an observer`(env: Env) {
        val instance = env.createFlatInstance()
        val owner = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val observer = env.createPlayer(instance, Pos(1.0, 42.0, 0.0))
        val engine = create(env, instance, NpcRenderAudience.Personal(owner))
        try {
            engine.publishVisible()
            val displays = instance.entities.filterIsInstance<NpcItemDisplay>()
            assertEquals(2, displays.size)
            assertTrue(displays.all { owner in it.viewers })
            assertTrue(displays.all { observer !in it.viewers })
            engine.refreshAudience()
            assertTrue(displays.all { observer !in it.viewers })
        } finally {
            engine.close()
        }
    }

    @Test
    fun `sample frame follows actual shared seek pause and stop even without viewers`(env: Env) {
        val instance = env.createFlatInstance()
        val engine = create(env, instance)
        try {
            engine.publishVisible()
            engine.startAnimation("wave", 0)
            engine.advanceAnimation(250)
            assertEquals("wave", engine.sampleFrame().clipId)
            assertEquals(0.25, engine.sampleFrame().seconds, 0.000001)
            engine.advanceAnimation(100)
            assertEquals(0.1, engine.sampleFrame().seconds, 0.000001)
            repeat(3) { engine.refreshAudience() }
            assertEquals(0.1, engine.sampleFrame().seconds, 0.000001)
            engine.stopAnimation(null)
            assertNull(engine.sampleFrame().clipId)
            assertEquals(0.0, engine.sampleFrame().seconds)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `personal frame can change head without changing its parent`(env: Env) {
        val instance = env.createFlatInstance()
        val owner = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val parent = create(env, instance)
        val parentParts =
            instance.entities.filterIsInstance<NpcItemDisplay>().sortedBy { it.entityId }
        val child = create(env, instance, NpcRenderAudience.Personal(owner))
        val childParts =
            instance.entities
                .filterIsInstance<NpcItemDisplay>()
                .filter { it !in parentParts }
                .sortedBy { it.entityId }
        try {
            parent.publishVisible()
            child.publishVisible()
            parent.startAnimation("wave", 250)
            val before = parent.sampleFrame()
            val parentHeadBefore = (parentParts[1].entityMeta as ItemDisplayMeta).translation
            val childHeadBefore = (childParts[1].entityMeta as ItemDisplayMeta).translation
            child.renderFrame(before.copy(headLook = NpcVector3(20.0, 40.0, 0.0)))
            assertEquals(before, parent.sampleFrame())
            assertEquals(
                parentHeadBefore,
                (parentParts[1].entityMeta as ItemDisplayMeta).translation,
            )
            assertNotEquals(
                childHeadBefore,
                (childParts[1].entityMeta as ItemDisplayMeta).translation,
            )
            assertEquals("wave", child.sampleFrame().clipId)
            assertEquals(0.25, child.sampleFrame().seconds)
            assertEquals(NpcVector3(20.0, 40.0, 0.0), child.sampleFrame().headLook)
            child.refreshAudience()
            assertEquals(NpcVector3(20.0, 40.0, 0.0), child.sampleFrame().headLook)
        } finally {
            child.close()
            parent.close()
        }
    }

    private fun create(
        env: Env,
        instance: Instance,
        audience: NpcRenderAudience = NpcRenderAudience.Shared,
    ): AnimatedNpcHandle {
        val base = NpcLifecycleTest.model()
        val model =
            NpcModel(
                base.id,
                base.bounds,
                base.bones,
                base.parts,
                mapOf("wave" to NpcClip("once", 1.0, emptyMap())),
                emptyMap(),
            )
        val lock = Any()
        val parts =
            model.parts.map { NpcItemDisplay(lock).also { it.updateViewableRule { false } } }
        val attaching =
            CompletableFuture.allOf(
                *parts.map { it.setInstance(instance, Pos(0.0, 42.0, 3.0)) }.toTypedArray()
            )
        assertTrue(env.tickWhile({ !attaching.isDone }, Duration.ofSeconds(3)))
        attaching.join()
        return AnimatedNpcHandle(
            lock,
            model,
            parts,
            NpcLifecycleTest.transform(),
            NpcSoundSink { _, _, _ -> },
            NpcRenderConfig(),
            audience,
        )
    }
}
