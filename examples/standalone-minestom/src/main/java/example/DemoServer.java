package example;

import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

/** Local Minestom and neutral resource-pack infrastructure, separate from NPC usage. */
final class DemoServer implements AutoCloseable {
  final net.minestom.server.instance.InstanceContainer instance;
  private final DemoPackHost pack;

  private DemoServer(net.minestom.server.instance.InstanceContainer instance, DemoPackHost pack) {
    this.instance = instance;
    this.pack = pack;
  }

  static DemoServer start(boolean ephemeralPorts) throws Exception {
    String packFile = System.getProperty("npc.demoPack");
    var pack =
        packFile == null
            ? null
            : new DemoPackHost(
                java.nio.file.Path.of(packFile),
                ephemeralPorts ? 0 : Integer.getInteger("npc.packPort", 25575));
    try {
      var server = MinecraftServer.init(new Auth.Offline());
      var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
      instance.setChunkSupplier(LightingChunk::new);
      instance.setTime(6000);
      // Freeze lighting for the demo. World age is independent of the NPC's clip-local clock.
      instance.defaultClock().pause();
      instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));
      MinecraftServer.getGlobalEventHandler()
          .addListener(
              AsyncPlayerConfigurationEvent.class,
              event -> {
                event.setSpawningInstance(instance);
                event.getPlayer().setRespawnPoint(new Pos(0, 40, 4, 180, 0));
              });
      if (pack != null)
        MinecraftServer.getGlobalEventHandler()
            .addListener(
                net.minestom.server.event.player.PlayerSpawnEvent.class,
                event -> {
                  if (event.isFirstSpawn()) event.getPlayer().sendResourcePacks(pack.request());
                });
      server.start("127.0.0.1", ephemeralPorts ? 0 : Integer.getInteger("npc.port", 25574));
      return new DemoServer(instance, pack);
    } catch (Exception failure) {
      if (pack != null) pack.close();
      MinecraftServer.stopCleanly();
      throw failure;
    }
  }

  @Override
  public void close() {
    try {
      if (pack != null) pack.close();
    } finally {
      MinecraftServer.stopCleanly();
    }
  }
}
