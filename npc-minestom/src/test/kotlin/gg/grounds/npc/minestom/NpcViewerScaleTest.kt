package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcViewerScaleTest {
    @Test
    fun `personal scale synchronizes interpolation for animated torso and static legs`() {
        MinecraftServer.init()
        val model =
            parseTestModel(
                """{"schemaVersion":1,"asset":"grounds:npc/test/scale_timing","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"root","parent":null,"origin":[0,0,0],"rotation":[0,0,0]},{"id":"body","parent":"root","origin":[0,1,0],"rotation":[0,0,0]},{"id":"legs","parent":"root","origin":[0,0,0],"rotation":[0,0,0]}],"parts":[{"bone":"body","model":"grounds:npc_parts/test/scale_timing/part_000","center":[0,1.25,0],"pivot":[0,1,0],"rotation":[0,0,0]},{"bone":"legs","model":"grounds:npc_parts/test/scale_timing/part_001","center":[0,0.25,0],"pivot":[0,0,0],"rotation":[0,0,0]}],"animations":{"idle":{"loop":"loop","length":2,"tracks":{"body":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":2,"value":[0,0.1,0],"interpolation":"linear"}]}}}},"sounds":{}}"""
            )

        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle =
                EngineTestRenderers(mapOf(model.id to model))
                    .rendererFor(model.id, AssetKind.NPC_BODY)!!
                    .create(
                        SceneAssetRenderContext(
                            fixture.instance,
                            LocalId("scale_timing"),
                            null,
                            model.id,
                            sceneTransform(),
                        )
                    )
                    .toCompletableFuture()
                    .join()
            try {
                val displays =
                    fixture.instance.entities.filterIsInstance<NpcItemDisplay>().sortedBy {
                        it.entityId
                    }
                handle.startAnimation(LocalId("idle"), 0)
                handle.advanceAnimation(200)
                // Both began at ten ticks before admission. Only the animated torso
                // subsequently received the nearby viewer's four-tick animation duration.
                assertEquals(
                    4,
                    (displays[0].entityMeta as ItemDisplayMeta).transformationInterpolationDuration,
                )
                assertEquals(
                    10,
                    (displays[1].entityMeta as ItemDisplayMeta).transformationInterpolationDuration,
                )
                val duration = MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index()
                fun capture(action: () -> Unit): List<EntityMetaDataPacket> {
                    player.connection.packets.clear()
                    action()
                    return displays.map { display ->
                        player.connection.packets.filterIsInstance<EntityMetaDataPacket>().last {
                            it.entityId() == display.entityId
                        }
                    }
                }
                val grow = capture {
                    handle.applyViewerState(player, SceneViewerVisualState(1.35, false))
                }
                val highlight = capture {
                    handle.applyViewerState(player, SceneViewerVisualState(1.35, true))
                }
                val restore = capture { handle.clearViewerState(player) }
                val reentry = capture {
                    displays.forEach { it.removeViewer(player) }
                    displays.forEach { it.addViewer(player) }
                }
                assertTrue(
                    highlight.all { it.entries().keys == setOf(0) },
                    "Highlight must not restart pose interpolation",
                )
                val poseFields =
                    setOf(
                        MetadataDef.Display.TRANSLATION.index(),
                        MetadataDef.Display.SCALE.index(),
                        MetadataDef.Display.ROTATION_LEFT.index(),
                        MetadataDef.Display.ROTATION_RIGHT.index(),
                        MetadataDef.Display.INTERPOLATION_DELAY.index(),
                    )
                assertAll(
                    listOf("grow" to grow, "restore" to restore, "reentry" to reentry).map {
                        (phase, packets) ->
                        org.junit.jupiter.api.function.Executable {
                            assertTrue(
                                packets.all { it.entries().keys.containsAll(poseFields) },
                                "$phase needs a coherent complete pose",
                            )
                            assertEquals(
                                listOf(4, 4),
                                packets.map { it.entries()[duration]?.value() },
                                "$phase must explicitly initialize the same interpolation duration for every part",
                            )
                        }
                    }
                )
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun `personal scale survives animation transforms and retracking without affecting another viewer`() {
        MinecraftServer.init()
        val model =
            parseTestModel(
                """{"schemaVersion":1,"asset":"grounds:npc/test/scaling","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"root","name":"root","parent":null,"origin":[0,0,0],"rotation":[0,0,0]}],"parts":[{"bone":"root","model":"grounds:npc_parts/test/scaling/part_000","center":[0,1,0],"pivot":[0,1,0],"rotation":[0,0,0]}],"animations":{"wave":{"loop":"once","length":2,"tracks":{"root":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":2,"value":[0,2,0],"interpolation":"linear"}]}}}},"sounds":{}}"""
            )

        RendererFixture().use { fixture ->
            val first = fixture.player()
            val second = fixture.player()
            val handle =
                EngineTestRenderers(mapOf(model.id to model))
                    .rendererFor(model.id, AssetKind.NPC_BODY)!!
                    .create(
                        SceneAssetRenderContext(
                            fixture.instance,
                            LocalId("scaled"),
                            null,
                            model.id,
                            sceneTransform(),
                        )
                    )
                    .toCompletableFuture()
                    .join()
            val display = fixture.instance.entities.filterIsInstance<NpcItemDisplay>().single()
            display.addViewer(first)
            display.addViewer(second)
            fun value(player: TestPlayer, field: Int): Point =
                player.connection.packets
                    .filterIsInstance<EntityMetaDataPacket>()
                    .filter { it.entityId() == display.entityId }
                    .mapNotNull { it.entries()[field]?.value() as? Point }
                    .last()
            val translation = MetadataDef.Display.TRANSLATION.index()
            val scale = MetadataDef.Display.SCALE.index()
            handle.startAnimation(LocalId("wave"), 0)
            handle.applyViewerState(first, SceneViewerVisualState(2.0, true))
            handle.advanceAnimation(500)
            fixture.instance.tick(500)
            assertEquals(3.0, value(first, translation).y(), 1e-6)
            assertEquals(1.5, value(second, translation).y(), 1e-6)
            assertEquals(2.0, value(first, scale).x(), 1e-6)
            assertEquals(1.0, value(second, scale).x(), 1e-6)
            assertEquals(1.5, (display.entityMeta as ItemDisplayMeta).translation.y(), 1e-6)
            handle.applyTransform(sceneTransform(Vec3(10.0, 0.0, 20.0)))
            handle.advanceAnimation(1000)
            fixture.instance.tick(1000)
            assertEquals(4.0, value(first, translation).y(), 1e-6)
            assertEquals(2.0, value(second, translation).y(), 1e-6)
            display.removeViewer(first)
            display.addViewer(first)
            assertEquals(4.0, value(first, translation).y(), 1e-6)
            assertTrue(first.connection.flags(display.entityId).last() and 0x40 != 0)
            handle.clearViewerState(first)
            assertEquals(2.0, value(first, translation).y(), 1e-6)
            assertEquals(1.0, value(first, scale).x(), 1e-6)
            handle.close()
            assertTrue(display.isRemoved)
        }
    }
}
