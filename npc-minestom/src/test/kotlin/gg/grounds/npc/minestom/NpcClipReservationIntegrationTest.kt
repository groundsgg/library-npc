package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.NpcPresentationLifecycleTest.Companion.await
import gg.grounds.npc.minestom.config.NpcRenderConfig
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcClipReservationIntegrationTest {
    @Test
    fun `nonzero direct start cannot bypass an unused reservation or extend its audio hold`(
        env: Env
    ) {
        val instance = env.createFlatInstance()
        val model =
            parseTestModel(
                """{"schemaVersion":1,"asset":"demo:worker","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[{"id":"root","name":"root","parent":null,"origin":[0,0,0],"rotation":[0,0,0]}],"parts":[{"bone":"root","model":"demo:part","center":[0,1,0],"pivot":[0,1,0],"rotation":[0,0,0]}],"animations":{"idle":{"loop":"loop","length":1,"tracks":{}},"wave":{"loop":"once","length":1,"tracks":{"root":{"position":[{"time":0,"value":[0,0,0],"interpolation":"linear"},{"time":1,"value":[0,1,0],"interpolation":"linear"}]}}}},"sounds":{"wave":{"key":"demo:wave","volume":0.8,"pitch":1,"radius":12,"durationMillis":5000}}}"""
            )
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest.builder(instance, model, NpcLifecycleTest.transform())
                    .config(
                        NpcRenderConfig.builder()
                            .idleClip("idle")
                            .protectSoundPlayback(true)
                            .build()
                    )
                    .build()
            )
        try {
            val handle = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
            await(env, handle.publish())
            val display = instance.entities.single { it.entityType == EntityType.ITEM_DISPLAY }
            val before = (display.entityMeta as ItemDisplayMeta).translation
            val lease = requireNotNull(handle.reserveClip("wave"))
            await(env, handle.playClip("wave", 500))
            assertEquals(before, (display.entityMeta as ItemDisplayMeta).translation)
            lease.close()
            assertTrue(
                handle.canStartClip("wave"),
                "An ignored direct command must not create an audible hold",
            )
        } finally {
            await(env, start.cancel())
        }
    }
}
