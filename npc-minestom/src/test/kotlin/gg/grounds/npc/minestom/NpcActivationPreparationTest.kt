package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import java.util.concurrent.CompletionException
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NpcActivationPreparationTest {
    private val model =
        NpcModel(
            AssetKey("test:prepared"),
            unitBounds,
            listOf(NpcBone("root", null, Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0))),
            listOf(0.5, 1.5).mapIndexed { index, y ->
                NpcPart(
                    "root",
                    "test:part_$index",
                    Vec3(0.0, y, 0.0),
                    Vec3(0.0, y, 0.0),
                    Vec3(0.0, 0.0, 0.0),
                )
            },
            emptyMap(),
            emptyMap(),
        )

    private fun registry() = EngineTestRenderers(mapOf(model.id to model))

    private fun create(fixture: RendererFixture, registry: EngineTestRenderers = registry()) =
        registry
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

    @Test
    fun `a rejected part never exposes an already attached sibling to the viewer`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            var attachments = 0
            fixture.instance.eventNode().addListener(AddEntityToInstanceEvent::class.java) {
                if (it.entity is NpcItemDisplay && ++attachments == 2) it.isCancelled = true
            }
            player.connection.packets.clear()
            assertThrows(CompletionException::class.java) { create(fixture).join() }
            assertEquals(2, attachments)
            assertTrue(
                player.connection.packets.filterIsInstance<SpawnEntityPacket>().isEmpty(),
                "Failed model exposed a partial body",
            )
            assertTrue(fixture.instance.entities.none { it is NpcItemDisplay })
        }
    }

    @Test
    fun `first spawn is published only after every model part has its initial pose`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val observed = mutableListOf<List<Double>>()
            player.connection.beforeSend = { packet ->
                if (packet is SpawnEntityPacket) {
                    observed +=
                        fixture.instance.entities
                            .filterIsInstance<NpcItemDisplay>()
                            .map { (it.entityMeta as ItemDisplayMeta).translation.y() }
                            .sorted()
                }
            }
            val handle = create(fixture).join()
            assertEquals(listOf(listOf(0.5, 1.5), listOf(0.5, 1.5)), observed)
            handle.close()
        }
    }

    @Test
    fun `changing one NPC item does not contaminate a later NPC from the same renderer`() {
        RendererFixture().use { fixture ->
            val registry = registry()
            val first = create(fixture, registry).join()
            fixture.instance.entities.filterIsInstance<NpcItemDisplay>().forEach {
                (it.entityMeta as ItemDisplayMeta).itemStack = ItemStack.of(Material.STONE)
            }
            first.close()
            val next = create(fixture, registry).join()
            val models =
                fixture.instance.entities
                    .filterIsInstance<NpcItemDisplay>()
                    .map {
                        val item = (it.entityMeta as ItemDisplayMeta).itemStack
                        assertEquals(Material.PAPER, item.material())
                        requireNotNull(item.get(DataComponents.ITEM_MODEL))
                    }
                    .sorted()
            assertEquals(listOf("test:part_0", "test:part_1"), models)
            next.close()
        }
    }

    @Test
    fun `packed flags retain non-highlight bits in snapshots and personal visual updates`() {
        RendererFixture().use { fixture ->
            val player = fixture.player()
            val display = NpcItemDisplay(Any())
            display.setAutoViewable(false)
            display.setInstance(fixture.instance).join()
            display.entityMeta.set(MetadataDef.ENTITY_FLAGS, 0xBD.toByte())
            display.visualState = { 1.0 to true }
            player.connection.packets.clear()
            display.sendPoseState(player, 5)
            display.sendVisualFlags(player)
            assertEquals(
                listOf(0xFD, 0xFD),
                player.connection.flags(display.entityId).map { it and 0xFF },
            )
            assertEquals(0xBD, display.entityMeta.get(MetadataDef.ENTITY_FLAGS).toInt() and 0xFF)
            display.visualState = { 1.0 to false }
            display.sendVisualFlags(player)
            assertEquals(0xBD, player.connection.flags(display.entityId).last() and 0xFF)
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
