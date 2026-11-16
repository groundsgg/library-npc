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

/** Owns the start token and clip task until all shared/private NPC cleanup has drained. */
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
      start = NpcRenderer.prepare(request);
      var prepared = await(start.ready());
      if (!(prepared instanceof NpcPrepareResult.Ready ready))
        throw new IllegalStateException(prepared.toString());
      var npc = ready.getHandle();
      await(npc.publish());
      var demo = new DemoApplication(server, start, npc);
      SharedNpcCommands.register(npc, demo.playback);
      if (personalCommands) PersonalNpcCommands.register(demo.personal);
      return demo;
    } catch (Exception failure) {
      try {
        if (start != null) await(start.cancel());
      } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
      } finally {
        server.close();
      }
      throw failure;
    }
  }

  static <T> T await(CompletionStage<T> stage) throws Exception {
    return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      await(playback.remove());
    } catch (Exception failure) {
      failure.printStackTrace();
    } finally {
      try {
        await(start.cancel());
      } catch (Exception failure) {
        failure.printStackTrace();
      } finally {
        server.close();
      }
    }
  }
}
