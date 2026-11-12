package example;

import gg.grounds.npc.minestom.NpcHandle;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcDemoPlaybackTest {
    @Test void commandAtLateUptimeStartsAtZeroAndRemovalStopsTheAdvanceDriver() {
        var starts = new ArrayList<Long>();
        var advances = new ArrayList<Long>();
        var closes = new AtomicInteger();
        var stops = new AtomicInteger();
        NpcHandle handle = (NpcHandle) Proxy.newProxyInstance(NpcHandle.class.getClassLoader(),new Class<?>[]{NpcHandle.class},(proxy,method,args) -> {
            switch (method.getName()) {
                case "startAnimation" -> starts.add((Long)args[1]);
                case "advanceAnimation" -> advances.add((Long)args[0]);
                case "closeAsync" -> closes.incrementAndGet();
                default -> throw new AssertionError("Unexpected call: " + method);
            }
            return CompletableFuture.completedFuture(null);
        });
        var playback = new NpcDemoPlayback(handle);
        playback.onRemoval(stops::incrementAndGet);
        for (int i=0;i<200;i++) playback.tick().toCompletableFuture().join();
        playback.start("wave").toCompletableFuture().join();
        playback.tick().toCompletableFuture().join();
        assertEquals(0L,starts.getLast());
        assertEquals(50L,advances.getLast());
        int before = advances.size();
        playback.remove().toCompletableFuture().join();
        playback.remove().toCompletableFuture().join();
        playback.tick().toCompletableFuture().join();
        assertEquals(before,advances.size());
        assertEquals(1,closes.get());
        assertEquals(1,stops.get());
    }
}
