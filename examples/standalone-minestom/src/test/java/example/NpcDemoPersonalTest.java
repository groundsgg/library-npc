package example;

import gg.grounds.npc.minestom.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcDemoPersonalTest {
    @Test void pendingCloseCannotPublishOverAReplacementAndLateUptimeStartsAtZero() throws Exception {
        MinecraftServer.init();
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0,0).join();
        var player = player(instance);
        var observer = player(instance);
        var start = NpcRenderer.prepare(new NpcSpawnRequest(instance,NpcDemo.model(),new NpcRenderTransform(new gg.grounds.npc.core.NpcTransform(gg.grounds.npc.core.NpcVector3.ZERO),null)));
        var parent = ((NpcPrepareResult.Ready)await(instance,start.ready())).getHandle();
        await(instance,parent.publish());
        var clock = new AtomicLong(10_000_000_000L);
        var demo = new NpcDemoPersonal(parent,p -> new NpcPresentationOptions(p,op -> {op.run();return true;},(p2,origin,sound) -> {},null,50,clock::get));
        try {
            var old = demo.open(player);
            demo.close(player).toCompletableFuture().get(1,TimeUnit.SECONDS);
            var fresh = await(instance,demo.open(player));
            assertThrows(Exception.class,() -> old.toCompletableFuture().get(1,TimeUnit.SECONDS));
            assertEquals(4,instance.getEntities().stream().filter(e -> e.getEntityType()==EntityType.ITEM_DISPLAY).count());
            await(instance,demo.with(player,h -> h.playClip("wave")));
            assertEquals(0.0,fresh.snapshot().getClipSeconds());
            clock.addAndGet(250_000_000);instance.tick(0);
            assertEquals(0.25,fresh.snapshot().getClipSeconds(),0.000001);
            assertThrows(Exception.class,() -> await(instance,demo.with(observer,h -> h.playClip("wave"))));
            demo.close(player).toCompletableFuture().get(1,TimeUnit.SECONDS);
            assertEquals(2,instance.getEntities().stream().filter(e -> e.getEntityType()==EntityType.ITEM_DISPLAY).count());
        } finally { start.cancel().toCompletableFuture().get(1,TimeUnit.SECONDS); }
    }
    private static Player player(Instance instance) {
        var player = new Player(new PlayerConnection() {
            @Override public void sendPacket(SendablePacket packet) {}
            @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress(0); }
        },new GameProfile(UUID.randomUUID(),"test"));
        player.setInstance(instance,Pos.ZERO).join(); return player;
    }
    private static <T> T await(Instance instance, CompletionStage<T> stage) throws Exception {
        var result=stage.toCompletableFuture();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!result.isDone() && System.nanoTime()<deadline) {instance.tick(0);Thread.yield();}
        return result.get(1,TimeUnit.SECONDS);
    }
}
