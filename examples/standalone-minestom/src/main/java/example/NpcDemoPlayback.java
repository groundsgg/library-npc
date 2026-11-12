package example;

import gg.grounds.npc.minestom.NpcHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Clip-local time and ownership of the sample's repeating advance task. */
final class NpcDemoPlayback {
    private final NpcHandle npc;
    private final CompletableFuture<Void> removed = new CompletableFuture<>();
    private long elapsed;
    private boolean closed;
    private Runnable cancelTicks = () -> {};

    NpcDemoPlayback(NpcHandle npc) { this.npc = npc; }

    synchronized CompletionStage<Void> tick() {
        if (closed) return CompletableFuture.completedFuture(null);
        return npc.advanceAnimation(elapsed += 50);
    }

    synchronized CompletionStage<Void> start(String clip) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Demo NPC removed"));
        elapsed = 0;
        return npc.startAnimation(clip,0);
    }

    void onRemoval(Runnable cancelTicks) {
        boolean stop;
        synchronized (this) { stop = closed; if (!stop) this.cancelTicks = cancelTicks; }
        if (stop) cancelTicks.run();
    }

    CompletionStage<Void> remove() {
        Runnable stop;
        synchronized (this) {
            if (closed) return removed.minimalCompletionStage();
            closed = true;
            stop = cancelTicks;
            cancelTicks = () -> {};
        }
        try {
            stop.run();
            npc.closeAsync().whenComplete((unused,error) -> {
                if (error == null) removed.complete(null); else removed.completeExceptionally(error);
            });
        } catch (Throwable error) { removed.completeExceptionally(error); }
        return removed.minimalCompletionStage();
    }
}
