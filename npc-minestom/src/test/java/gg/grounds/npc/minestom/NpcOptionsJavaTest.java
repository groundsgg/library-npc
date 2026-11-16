package gg.grounds.npc.minestom;

import gg.grounds.npc.minestom.config.NpcRenderConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcOptionsJavaTest {
    @Test void reusedBuilderCannotMutateBuiltOptions() {
        var builder = NpcRenderConfig.builder().idleClip("idle");
        var first = builder.build();
        var second = builder.idleClip("other").build();
        assertEquals("idle", first.getIdleClip());
        assertEquals("other", second.getIdleClip());
        assertEquals(200, first.getNearIntervalMillis());
        var copy = first.toBuilder().nearIntervalMillis(100).build();
        assertEquals(200, first.getNearIntervalMillis());
        assertEquals(100, copy.getNearIntervalMillis());
    }
    @Test void invalidIntervalsIdentifyTheProperty() {
        var failure = assertThrows(IllegalArgumentException.class,
            () -> NpcRenderConfig.builder().nearIntervalMillis(51).build());
        assertTrue(failure.getMessage().contains("nearIntervalMillis"));
    }
}
