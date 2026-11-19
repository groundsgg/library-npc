package gg.grounds.npc.minestom

import gg.grounds.npc.minestom.internal.playback.NpcClipPlaybackGate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcClipPlaybackGateTest {
    private fun json(duration: String = "5000", pitch: String = "1") =
        """{"schemaVersion":1,"asset":"demo:worker","bounds":{"center":[0,1,0],"size":[1,2,1]},"bones":[],"parts":[{"bone":null,"model":"demo:part","center":[0,1,0],"pivot":[0,1,0],"rotation":[0,0,0]}],"animations":{"idle":{"loop":"loop","length":1,"tracks":{}},"wave":{"loop":"once","length":1,"tracks":{}}},"sounds":{"wave":{"key":"demo:wave","volume":0.8,"pitch":$pitch,"radius":12,"durationMillis":$duration}}}"""

    private fun model(pitch: String = "1") = parseTestModel(json(pitch = pitch))

    @Test
    fun `duration survives decode and slow pitch extends audible hold`() {
        val model = model(pitch = "0.5")
        assertEquals(5000L, model.sounds.getValue("wave").durationMillis)
        val clock = AtomicLong(100)
        val gate = NpcClipPlaybackGate(model, true, clock::get)
        gate.started("wave", 0)
        clock.addAndGet(9_000_000_000)
        assertFalse(gate.canStart("wave"))
        assertTrue(gate.canStart("idle"))
        clock.addAndGet(1_100_000_000)
        assertTrue(gate.canStart("wave"))
    }

    @Test
    fun `reservation remains exclusive until queued start drains even when lease closes`() {
        val clock = AtomicLong()
        val gate = NpcClipPlaybackGate(model(), true, clock::get)
        val queued = CompletableFuture<Void>()
        val lease = requireNotNull(gate.reserve("wave") { _, _ -> queued })
        assertNull(gate.reserve("wave") { _, _ -> CompletableFuture.completedFuture(null) })
        lease.start(0)
        lease.close()
        clock.set(20_000_000_000)
        assertFalse(gate.canStart("wave"))
        gate.started("wave", 0)
        queued.complete(null)
        assertFalse(gate.canStart("wave"))
        clock.addAndGet(5_100_000_000)
        assertNotNull(gate.reserve("wave") { _, _ -> CompletableFuture.completedFuture(null) })
    }

    @Test
    fun `cancelled unused lease and failed queued start release reservation`() {
        val gate = NpcClipPlaybackGate(model(), true) { 0 }
        val lease = requireNotNull(gate.reserve("wave") { _, _ -> error("must not run") })
        lease.close()
        assertThrows(IllegalStateException::class.java) { lease.start(0) }
        val rejected =
            requireNotNull(
                gate.reserve("wave") { _, _ ->
                    CompletableFuture.failedFuture(IllegalStateException())
                }
            )
        assertTrue(rejected.start(0).toCompletableFuture().isCompletedExceptionally)
        rejected.close()
        assertTrue(gate.canStart("wave"))
        gate.close()
        assertFalse(gate.canStart("idle"))
        assertNull(gate.reserve("wave") { _, _ -> error("closed") })
    }

    @Test
    fun `concurrent clip starts admit only one audible gesture`() {
        val gate = NpcClipPlaybackGate(model(), true) { 0 }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val start = java.util.concurrent.CountDownLatch(1)
            val attempts =
                (0 until 32).map {
                    pool.submit<Boolean> {
                        start.await()
                        gate.tryStart("wave", 0)
                    }
                }
            start.countDown()
            assertEquals(1, attempts.count { it.get(2, java.util.concurrent.TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `positive offset restoration remains allowed during an ordinary audio hold`() {
        val gate = NpcClipPlaybackGate(model(), true) { 0 }
        gate.started("wave", 0)
        assertFalse(gate.canStart("wave"))
        assertTrue(gate.tryStart("wave", 500))
    }

    @Test
    fun `duration must be a bounded exact integer`() {
        for (bad in listOf("-1", "300001", "1.5", "true", "\"5\"")) {
            assertInstanceOf(
                gg.grounds.npc.core.io.NpcDecodeResult.Failure::class.java,
                gg.grounds.npc.core.io.NpcModelJson.decode(json(duration = bad).toByteArray()),
            )
        }
    }
}
