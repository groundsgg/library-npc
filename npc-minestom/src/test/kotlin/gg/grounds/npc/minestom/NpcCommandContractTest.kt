package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.NpcPresentationLifecycleTest.Companion.await
import gg.grounds.npc.minestom.presentation.NpcPresentationOptions
import net.minestom.server.coordinate.Pos
import net.minestom.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@EnvTest
class NpcCommandContractTest {
    @Test
    fun `closed observation does not close and commands keep errors in their stages`(env: Env) {
        val instance = env.createFlatInstance()
        val player = env.createPlayer(instance, Pos(0.0, 42.0, 0.0))
        val start =
            NpcRenderer.prepare(
                NpcSpawnRequest.builder(instance, personalTestModel(), NpcLifecycleTest.transform())
                    .build()
            )
        val handle = (await(env, start.ready()) as NpcPrepareResult.Ready).handle
        await(env, handle.publish())
        val observation = handle.closed().toCompletableFuture()
        assertFalse(observation.isDone)
        val invalid =
            assertDoesNotThrow<java.util.concurrent.CompletionStage<Void>> {
                handle.playClip("missing")
            }
        val failure =
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                await(env, invalid)
            }
        assertInstanceOf(IllegalArgumentException::class.java, failure.cause)
        await(env, handle.playClip("wave"))
        await(env, handle.advanceClipTo(250))
        val personal = handle.preparePresentation(NpcPresentationOptions.builder(player).build())
        await(env, personal.cancel())
        await(env, handle.stopClipIfPlaying("other"))
        await(env, handle.stopClip())
        await(env, handle.closeAsync())
        assertTrue(observation.isDone)
        assertFalse(observation.isCompletedExceptionally)
        val after =
            assertDoesNotThrow<java.util.concurrent.CompletionStage<Void>> {
                handle.playClip("wave")
            }
        assertTrue(after.toCompletableFuture().isCompletedExceptionally)
    }
}
