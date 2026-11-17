package example;

import gg.grounds.npc.minestom.*;
import gg.grounds.npc.minestom.NpcHandle;
import gg.grounds.npc.minestom.viewer.NpcViewerState;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;

/** Commands for the common NPC; every asynchronous error is observed. */
final class SharedNpcCommands {
  static void register(NpcHandle npc, SharedNpcPlayback playback) {
    command("npcwave", () -> playback.start("wave"));
    command("npcidle", () -> playback.start("idle"));
    command("npclook", () -> npc.applyHeadLook(20, -10));
    command("npcmove", () -> npc.applyTransform(DemoNpcModel.at(2)));
    command("npcremove", playback::remove);
    // Scale/glow is an override for the command sender's connection, not a global model edit.
    PersonalNpcCommands.playerCommand(
        "npcscale", player -> npc.applyViewerState(player, new NpcViewerState(1.3, true)));
  }

  private static void command(
      String name, java.util.function.Supplier<CompletionStage<Void>> operation) {
    var command = new Command(name);
    command.setDefaultExecutor(
        (sender, context) ->
            operation
                .get()
                .exceptionally(
                    error -> {
                      error.printStackTrace();
                      return null;
                    }));
    MinecraftServer.getCommandManager().register(command);
  }
}
