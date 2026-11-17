package example;

import gg.grounds.npc.minestom.*;
import gg.grounds.npc.minestom.presentation.NpcPresentationChannel;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;

/** Automated protocol/lifecycle checks; never mixed into the interactive examples. */
public final class NpcDemoSmoke {
  public static void main(String[] args) throws Exception {
    try (var demo = DemoApplication.start(DemoServer.start(true), true)) {
      var instance = demo.server.instance;
      var npc = demo.npc;
      var personal = demo.personal;
      var playback = demo.playback;
      var a = smokePlayer(instance);
      var b = smokePlayer(instance);
      var observer = smokePlayer(instance);
      var privateA = personal.open(a).toCompletableFuture().get(5, TimeUnit.SECONDS);
      var privateB = personal.open(b).toCompletableFuture().get(5, TimeUnit.SECONDS);
      personal.with(a, h -> h.playClip("wave")).toCompletableFuture().get(5, TimeUnit.SECONDS);
      personal
          .with(b, h -> h.applyHeadLook(-30, 10))
          .toCompletableFuture()
          .get(5, TimeUnit.SECONDS);
      if (privateA.snapshot().getClipMode() != NpcPresentationChannel.PRIVATE
          || privateB.snapshot().getClipMode() != NpcPresentationChannel.FOLLOW_SHARED)
        throw new AssertionError("Independent clip channels");
      if (instance.getEntities().stream()
              .filter(e -> e.getEntityType() == EntityType.ITEM_DISPLAY)
              .count()
          != 6) throw new AssertionError("Common plus A/B groups");
      personal.close(a).toCompletableFuture().get(5, TimeUnit.SECONDS);
      personal.close(b).toCompletableFuture().get(5, TimeUnit.SECONDS);
      a.remove();
      b.remove();
      observer.remove();
      playback.start("wave").toCompletableFuture().get(5, TimeUnit.SECONDS);
      npc.applyHeadLook(20, -10).toCompletableFuture().get(5, TimeUnit.SECONDS);
      npc.applyTransform(DemoNpcModel.at(512)).toCompletableFuture().get(5, TimeUnit.SECONDS);
      if (instance.getEntities().stream()
              .filter(e -> e.getEntityType() == EntityType.ITEM_DISPLAY)
              .count()
          != 2) throw new AssertionError("Expected two parts");
      playback.remove().toCompletableFuture().get(5, TimeUnit.SECONDS);
      if (instance.getEntities().stream()
          .anyMatch(e -> e.getEntityType() == EntityType.ITEM_DISPLAY))
        throw new AssertionError("Leaked display");
      System.out.println(
          "NPC standalone smoke OK: spawn, A/B private channels, private close, wave, headlook,"
              + " move, close");
    }
  }

  private static net.minestom.server.entity.Player smokePlayer(
      net.minestom.server.instance.Instance instance) {
    var connection =
        new net.minestom.server.network.player.PlayerConnection() {
          @Override
          public void sendPacket(net.minestom.server.network.packet.server.SendablePacket packet) {}

          @Override
          public java.net.SocketAddress getRemoteAddress() {
            return new java.net.InetSocketAddress(0);
          }
        };
    var player =
        new net.minestom.server.entity.Player(
            connection,
            new net.minestom.server.network.player.GameProfile(UUID.randomUUID(), "smoke"));
    player.setInstance(instance, new Pos(0, 40, 4)).join();
    return player;
  }
}
