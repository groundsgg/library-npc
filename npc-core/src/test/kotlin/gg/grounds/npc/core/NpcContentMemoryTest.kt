package gg.grounds.npc.core

import gg.grounds.npc.core.io.content.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcContentMemoryTest {
    @Test
    fun validatesAnInMemoryCompilationWithoutTemporaryContentFiles() {
        val fixture = ContentFixture()
        val resources = fixture.files + ("bundle.json" to fixture.contract.toString().toByteArray())
        val content = NpcContentLoader.load(resources)
        content.verifyResourcePack(
            mapOf(
                "pack.mcmeta" to
                    """{"pack":{"pack_format":97,"min_format":[97,1],"max_format":[97,1]}}"""
                        .toByteArray(),
                "assets/demo/items/part.json" to fixture.client,
                "assets/demo/sounds.json" to """{"wave":{"sounds":["demo:wave"]}}""".toByteArray(),
            )
        )
        fixture.files.getValue("models/cube.json")[0] = 0
        assertEquals("demo:npc/cube", content.models.values.single().id)
    }

    @Test
    fun refusesCaseAmbiguousInMemoryResources() {
        val f = ContentFixture()
        assertThrows(NpcContentException::class.java) {
            NpcContentLoader.load(
                f.files +
                    ("bundle.json" to f.contract.toString().toByteArray()) +
                    ("models/Cube.json" to byteArrayOf(1))
            )
        }
    }
}
