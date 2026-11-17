package gg.grounds.npc.core

import gg.grounds.npc.core.io.content.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NpcContentPackTest {
    @TempDir lateinit var temp: Path
    private val fixture = ContentFixture()

    private fun pack(extra: Map<String, ByteArray> = emptyMap()): Path =
        ContentFixture.zipFiles(
            temp.resolve("pack.zip"),
            mapOf(
                "pack.mcmeta" to
                    """{"pack":{"pack_format":97,"min_format":[97,1],"max_format":[97,1],"description":"test"}}"""
                        .toByteArray(),
                "assets/demo/items/part.json" to fixture.client,
                "assets/demo/sounds.json" to
                    """{"wave":{"sounds":["demo:wave"]},"unrelated":{"sounds":["demo:other"]}}"""
                        .toByteArray(),
                "assets/other/anything.txt" to byteArrayOf(12),
            ) + extra,
        )

    @Test
    fun acceptsForeignAssetsAndDisjointSoundEntries() {
        val content = NpcContentLoader.load(fixture.zip(temp.resolve("content.zip")))
        content.verifyResourcePack(pack())
    }

    @Test
    fun rejectsChangedOwnSoundDefinition() {
        val content = NpcContentLoader.load(fixture.zip(temp.resolve("content.zip")))
        assertThrows(NpcContentException::class.java) {
            content.verifyResourcePack(
                pack(
                    mapOf(
                        "assets/demo/sounds.json" to
                            """{"wave":{"sounds":["demo:wrong"]}}""".toByteArray()
                    )
                )
            )
        }
    }

    @Test
    fun rejectsChangedDedicatedAsset() {
        val content = NpcContentLoader.load(fixture.zip(temp.resolve("content.zip")))
        assertThrows(NpcContentException::class.java) {
            content.verifyResourcePack(
                pack(mapOf("assets/demo/items/part.json" to "{}".toByteArray()))
            )
        }
    }

    @Test
    fun rejectsWrongTargetAndActiveOverlayShadowing() {
        val content = NpcContentLoader.load(fixture.zip(temp.resolve("content.zip")))
        val metadata =
            """{"pack":{"pack_format":97,"min_format":[97,1],"max_format":[97,1],"description":"test"},"overlays":{"entries":[{"formats":{"min_inclusive":97,"max_inclusive":97},"directory":"overlay"}]}}"""
        assertThrows(NpcContentException::class.java) {
            content.verifyResourcePack(
                pack(
                    mapOf(
                        "pack.mcmeta" to metadata.toByteArray(),
                        "overlay/assets/demo/items/part.json" to "{}".toByteArray(),
                    )
                )
            )
        }
        assertThrows(NpcContentException::class.java) {
            content.verifyResourcePack(
                pack(
                    mapOf(
                        "pack.mcmeta" to
                            """{"pack":{"pack_format":88,"min_format":[88,0],"max_format":[88,0]}}"""
                                .toByteArray()
                    )
                )
            )
        }
    }
}
