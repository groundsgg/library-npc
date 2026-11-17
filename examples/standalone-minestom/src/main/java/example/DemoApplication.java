package example;

import gg.grounds.npc.minestom.*;
import gg.grounds.npc.minestom.NpcHandle;
import gg.grounds.npc.minestom.NpcPrepareResult;
import gg.grounds.npc.minestom.NpcRenderer;
import gg.grounds.npc.minestom.NpcSpawnRequest;
import gg.grounds.npc.minestom.NpcStart;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.timer.TaskSchedule;

/**
 * The example's lifecycle owner: prepare hidden parts, publish them, then drain them before the
 * server stops. Keep this owner even if preparation fails before it returns a handle.
 *
 * <p>The blocking {@link #await} helper is confined to bootstrap and shutdown threads. NPC commands
 * run on the instance scheduler, so an instance-tick callback must compose their stages instead of
 * waiting synchronously for work on that same scheduler.
 */
final class DemoApplication implements AutoCloseable {
  final DemoServer server;
  final NpcHandle npc;
  final SharedNpcPlayback playback;
  final PersonalNpcSessions personal;
  private final NpcStart start;
  private final java.util.concurrent.atomic.AtomicBoolean closed =
      new java.util.concurrent.atomic.AtomicBoolean();

  private DemoApplication(DemoServer server, NpcStart start, NpcHandle npc) {
    this.server = server;
    this.start = start;
    this.npc = npc;
    this.playback = new SharedNpcPlayback(npc);
    this.personal = new PersonalNpcSessions(npc, PersonalNpcCommands::options);
    // Shared animation uses a host-owned clip clock. The library does not install this task.
    var ticking =
        server
            .instance
            .scheduler()
            .buildTask(playback::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule();
    playback.onRemoval(ticking::cancel);
  }

  static DemoApplication start(DemoServer server, boolean personalCommands) throws Exception {
    NpcStart start = null;
    try {
      var request =
          NpcSpawnRequest.builder(server.instance, DemoNpcModel.create(), DemoNpcModel.at(0))
              .build();
      // Retain the cancellation token immediately, before asynchronous attachment can finish.
      start = NpcRenderer.prepare(request);
      var prepared = await(start.ready());
      if (!(prepared instanceof NpcPrepareResult.Ready ready))
        throw new IllegalStateException(prepared.toString());
      var npc = ready.getHandle();
      // Ready means allocated and hidden. Publication is the separate visibility boundary.
      await(npc.publish());
      var demo = new DemoApplication(server, start, npc);
      SharedNpcCommands.register(npc, demo.playback);
      if (personalCommands) PersonalNpcCommands.register(demo.personal);
      return demo;
    } catch (Exception failure) {
      try {
        // This also handles rejection or failure before any usable handle was returned.
        if (start != null) await(start.cancel());
      } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
      } finally {
        server.close();
      }
      throw failure;
    }
  }

  /** Bootstrap/shutdown convenience; never call this from an instance-tick callback. */
  static <T> T await(CompletionStage<T> stage) throws Exception {
    return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      // Stop the host's repeating task before closing the NPC it would otherwise keep advancing.
      await(playback.remove());
    } catch (Exception failure) {
      failure.printStackTrace();
    } finally {
      try {
        // Parent cleanup includes pending/active private presentations and accepted sound/I/O work.
        await(start.cancel());
      } catch (Exception failure) {
        failure.printStackTrace();
      } finally {
        server.close();
      }
    }
  }
}
