package example;

import gg.grounds.npc.minestom.*;
import gg.grounds.npc.minestom.presentation.NpcPresentationHandle;
import gg.grounds.npc.minestom.presentation.NpcPresentationOptions;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;

/** The optional presentation scenario: independent channels belong to a concrete connection. */
final class PersonalNpcCommands {
  static NpcPresentationOptions options(net.minestom.server.entity.Player player) {
    return NpcPresentationOptions.builder(player)
        .sounds(
            (recipient, origin, sound) ->
                recipient.playSound(
                    net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key(sound.getKey()),
                        net.kyori.adventure.sound.Sound.Source.MASTER,
                        (float) sound.getVolume(),
                        (float) sound.getPitch())))
        .build();
  }

  static void register(PersonalNpcSessions personal) {
    playerCommand("personal", player -> personal.open(player).thenApply(handle -> null));
    playerCommand(
        "personalwave", player -> personal.with(player, handle -> handle.playClip("wave")));
    playerCommand(
        "personalidle", player -> personal.with(player, handle -> handle.playClip("idle")));
    playerCommand("personalstop", player -> personal.with(player, NpcPresentationHandle::stopClip));
    playerCommand(
        "personallook", player -> personal.with(player, handle -> handle.applyHeadLook(35, -15)));
    playerCommand(
        "personalfollow",
        player ->
            personal.with(
                player,
                handle ->
                    handle
                        .followSharedClip()
                        .thenCompose(ignored -> handle.followSharedHeadLook())));
    playerCommand("personalclose", personal::close);
  }

  static void playerCommand(
      String name,
      java.util.function.Function<net.minestom.server.entity.Player, CompletionStage<Void>>
          operation) {
    var command = new Command(name);
    command.setDefaultExecutor(
        (sender, context) -> {
          if (sender instanceof net.minestom.server.entity.Player player)
            operation
                .apply(player)
                .exceptionally(
                    error -> {
                      player.sendMessage("NPC demo: " + error.getMessage());
                      return null;
                    });
        });
    MinecraftServer.getCommandManager().register(command);
  }
}
