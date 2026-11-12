package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcHeadLookRendererTest {
    @Test
    fun `rendered head look changes only the head subtree and preserves personal visuals`() {
        MinecraftServer.init()
        val model =
            NpcModel(
                AssetKey("test:headed"),
                NpcBounds(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 2.0, 1.0)),
                listOf(
                    NpcBone("body", null, Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0)),
                    NpcBone("head-id", "body", Vec3(0.0, 1.5, 0.0), Vec3(0.0, 0.0, 0.0), "head"),
                ),
                listOf(
                    NpcPart(
                        "body",
                        "test:body",
                        Vec3(0.0, 0.7, 0.0),
                        Vec3(0.0, 0.7, 0.0),
                        Vec3(0.0, 0.0, 0.0),
                    ),
                    NpcPart(
                        "head-id",
                        "test:head",
                        Vec3(0.0, 1.8, 0.0),
                        Vec3(0.0, 1.8, 0.0),
                        Vec3(0.0, 0.0, 0.0),
                    ),
                ),
                emptyMap(),
                emptyMap(),
            )

        RendererFixture().use { fixture ->
            val player = fixture.player()
            val handle =
                EngineTestRenderers(mapOf(model.id to model))
                    .rendererFor(model.id, AssetKind.NPC_BODY)!!
                    .create(
                        SceneAssetRenderContext(
                            fixture.instance,
                            LocalId("headed"),
                            null,
                            model.id,
                            sceneTransform(),
                        )
                    )
                    .toCompletableFuture()
                    .join()
            val displays =
                fixture.instance.entities.filterIsInstance<NpcItemDisplay>().sortedBy {
                    it.entityId
                }
            displays.forEach { it.addViewer(player) }
            val body = displays[0].entityMeta as ItemDisplayMeta
            val head = displays[1].entityMeta as ItemDisplayMeta
            val translation = body.translation
            val rotation = body.leftRotation.copyOf()
            val oldHead = head.translation
            handle.applyViewerState(player, SceneViewerVisualState(1.35, true))
            val look = handle as HeadLookRenderedAssetHandle
            assertEquals(Vec3(0.0, 1.65, 0.0), look.headLookOrigin)
            look.applyHeadLook(35.0, -25.0)
            assertEquals(translation, body.translation)
            assertArrayEquals(rotation, body.leftRotation, 1e-6f)
            assertNotEquals(oldHead, head.translation)
            assertEquals(1.35, displays[1].visualState!!(player).first, 1e-8)
            assertTrue(displays[1].visualState!!(player).second)
            handle.close()
            assertTrue(displays.all { it.isRemoved })
        }
    }
}
