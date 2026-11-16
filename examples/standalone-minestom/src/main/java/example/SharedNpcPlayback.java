package example;

import gg.grounds.npc.minestom.NpcHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Owns the shared clip clock and its repeating scheduler task.
 *
 * <p>{@code advanceClipTo} takes absolute clip-local milliseconds. At a nominal 20 ticks per second
 * this demo advances by 50 ms per tick, producing 50, 100, 150... rather than passing a repeated
 * delta of 50. It deliberately uses tick time, so animation slows with a lagging server. Numeric
 * model keyframes use seconds; personal presentations use their own monotonic clock inside the
 * library.
 */
final class SharedNpcPlayback {
  private final NpcHandle npc;
  private final CompletableFuture<Void> removed = new CompletableFuture<>();
  private long elapsed;
  private boolean closed;
  private Runnable cancelTicks = () -> {};

  SharedNpcPlayback(NpcHandle npc) {
    this.npc = npc;
  }

  synchronized CompletionStage<Void> tick() {
    if (closed) return CompletableFuture.completedFuture(null);
    return npc.advanceClipTo(elapsed += 50);
  }

  synchronized CompletionStage<Void> start(String clip) {
    if (closed)
      return CompletableFuture.failedFuture(new IllegalStateException("Demo NPC removed"));
    // A new clip starts at zero regardless of how long the server has already been running.
    elapsed = 0;
    return npc.playClip(clip, 0);
  }

  void onRemoval(Runnable cancelTicks) {
    boolean stop;
    synchronized (this) {
      stop = closed;
      if (!stop) this.cancelTicks = cancelTicks;
    }
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
      // Cancel the host driver first; observe the asynchronous renderer drain before reporting
      // done.
      stop.run();
      npc.closeAsync()
          .whenComplete(
              (unused, error) -> {
                if (error == null) removed.complete(null);
                else removed.completeExceptionally(error);
              });
    } catch (Throwable error) {
      removed.completeExceptionally(error);
    }
    return removed.minimalCompletionStage();
  }
}
