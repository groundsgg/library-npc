package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AnimatedNpcRendererTest {
    private fun model() =
        parseTestModel(
            """{"schemaVersion":1,"asset":"grounds:npc/test/worker","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"root","name":"root","parent":null,"origin":[0,0,0],"rotation":[0,0,0]}],"parts":[{"bone":"root","model":"grounds:npc_parts/test/worker/part_000","center":[0,1,0],"pivot":[0,1,0],"rotation":[0,0,0]}],"animations":{"idle":{"loop":"loop","length":2,"tracks":{}},"wave":{"loop":"once","length":1,"tracks":{"root":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":1,"value":[0,1,0],"interpolation":"linear"}]}}},"hidden":{"loop":"once","length":1,"tracks":{"root":{"scale":[{"time":0,"value":[0,0,0],"interpolation":"linear"}]}}}},"sounds":{}}"""
        )

    @Test
    fun `source animation moves the rendered part and a one-shot returns to idle`() {
        RendererFixture().use { fixture ->
            fixture.player(net.minestom.server.coordinate.Pos(10.0, 140.0, 20.0))
            val model = model()

            val registry = EngineTestRenderers(mapOf(model.id to model))
            val handle =
                registry
                    .rendererFor(model.id, AssetKind.NPC_BODY)!!
                    .create(
                        SceneAssetRenderContext(
                            fixture.instance,
                            LocalId("npc"),
                            null,
                            model.id,
                            sceneTransform(Vec3(10.0, 140.0, 20.0)),
                        )
                    )
                    .toCompletableFuture()
                    .join()
            val display =
                fixture.instance.entities.single { it.entityType == EntityType.ITEM_DISPLAY }
            val meta = display.entityMeta as ItemDisplayMeta
            assertEquals(
                "grounds:npc_parts/test/worker/part_000",
                meta.itemStack.get(DataComponents.ITEM_MODEL),
            )
            handle.startAnimation(LocalId("wave"), 0)
            handle.advanceAnimation(500)
            assertEquals(1.5, meta.translation.y(), 1e-6)
            handle.advanceAnimation(1500)
            assertEquals(1.0, meta.translation.y(), 1e-6)
            handle.startAnimation(LocalId("hidden"), 0)
            assertEquals(0.0, meta.scale.length(), 1e-6)
            assertTrue(meta.leftRotation.all { it.isFinite() })
            val player = fixture.player(net.minestom.server.coordinate.Pos(10.0, 140.0, 20.0))
            handle.applyViewerState(player, SceneViewerVisualState(highlighted = true))
            assertTrue(player.connection.flags(display.entityId).last() and 0x40 != 0)
            handle.close()
            handle.advanceAnimation(2000)
            assertTrue(display.isRemoved)
            assertNull(registry.rendererFor(model.id, AssetKind.PROP))
        }
    }

    @Test
    fun `unchanged parts do not restart old client interpolation`() {
        RendererFixture().use { fixture ->
            val model = model()

            val player = fixture.player()
            val handle =
                EngineTestRenderers(mapOf(model.id to model))
                    .rendererFor(model.id, AssetKind.NPC_BODY)!!
                    .create(
                        SceneAssetRenderContext(
                            fixture.instance,
                            LocalId("npc"),
                            null,
                            model.id,
                            sceneTransform(),
                        )
                    )
                    .toCompletableFuture()
                    .join()
            handle.startAnimation(LocalId("idle"), 0)
            // Flush the entity's batched metadata before isolating steady-state updates.
            fixture.instance.tick(0)
            player.connection.packets.clear()
            for (time in listOf(100L, 200L, 300L)) {
                handle.advanceAnimation(time)
                fixture.instance.tick(time)
            }
            val updates =
                player.connection.packets.filterIsInstance<
                    net.minestom.server.network.packet.server.play.EntityMetaDataPacket
                >()
            assertTrue(updates.isEmpty(), "Static pose emitted repeated metadata: $updates")
            handle.close()
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun boot() {
            MinecraftServer.init()
        }
    }
}
