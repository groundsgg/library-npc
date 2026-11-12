package example;

import gg.grounds.npc.core.*;
import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;

/** A server using only the published NPC API and public Minestom. */
public final class NpcDemo {
    public static void main(String[] args) throws Exception {
        boolean smoke = Arrays.asList(args).contains("--smoke");
        String packFile = System.getProperty("npc.demoPack");
        var pack = packFile == null ? null : new DemoPackHost(java.nio.file.Path.of(packFile),smoke ? 0 : 25575);
        var server = MinecraftServer.init(new Auth.Offline());
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setTime(6000);
        instance.defaultClock().pause();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0,40,Block.GRASS_BLOCK));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class,event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(new Pos(0,40,4,180,0));
        });
        if (pack != null) MinecraftServer.getGlobalEventHandler().addListener(
            net.minestom.server.event.player.PlayerSpawnEvent.class,event -> {
                if (event.isFirstSpawn()) event.getPlayer().sendResourcePacks(pack.request());
            });
        var start = NpcRenderer.prepare(new NpcSpawnRequest(instance,model(),at(0)));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { start.cancel().toCompletableFuture().get(5,TimeUnit.SECONDS); }
            catch (Exception failure) { failure.printStackTrace(); }
            if (pack != null) pack.close();
            MinecraftServer.stopCleanly();
        }));
        server.start("127.0.0.1",smoke ? 0 : 25574);
        try {
            var prepared = start.ready().toCompletableFuture().get(5,TimeUnit.SECONDS);
            if (!(prepared instanceof NpcPrepareResult.Ready ready)) throw new IllegalStateException(prepared.toString());
            var npc = ready.getHandle();
            npc.publish().toCompletableFuture().get(5,TimeUnit.SECONDS);
            var playback = new NpcDemoPlayback(npc);
            var ticking = instance.scheduler().buildTask(playback::tick)
                .repeat(TaskSchedule.tick(1)).schedule();
            playback.onRemoval(ticking::cancel);
            command("npcwave", () -> playback.start("wave"));
            command("npcidle", () -> playback.start("idle"));
            command("npclook", () -> npc.applyHeadLook(20,-10));
            command("npcmove", () -> npc.applyTransform(at(2)));
            command("npcremove", playback::remove);
            var scale = new Command("npcscale");
            scale.setDefaultExecutor((sender,context) -> {
                if (sender instanceof net.minestom.server.entity.Player player)
                    npc.applyViewerState(player,new NpcViewerState(1.3,true));
            });
            MinecraftServer.getCommandManager().register(scale);
            if (smoke) {
                playback.start("wave").toCompletableFuture().get(5,TimeUnit.SECONDS);
                npc.applyHeadLook(20,-10).toCompletableFuture().get(5,TimeUnit.SECONDS);
                npc.applyTransform(at(512)).toCompletableFuture().get(5,TimeUnit.SECONDS);
                if (instance.getEntities().stream().filter(e -> e.getEntityType() == EntityType.ITEM_DISPLAY).count() != 2)
                    throw new AssertionError("Expected two parts");
                playback.remove().toCompletableFuture().get(5,TimeUnit.SECONDS);
                if (instance.getEntities().stream().anyMatch(e -> e.getEntityType() == EntityType.ITEM_DISPLAY))
                    throw new AssertionError("Leaked display");
                System.out.println("NPC standalone smoke OK: spawn, wave, headlook, move, close");
            } else System.out.println("NPC demo on localhost:25574. Accept the original demo pack. Commands: /npcwave /npcidle /npclook /npcscale /npcmove /npcremove");
        } finally { if (smoke) { start.cancel().toCompletableFuture().get(5,TimeUnit.SECONDS); if (pack != null) pack.close(); MinecraftServer.stopCleanly(); } }
    }
    private static void command(String name, java.util.function.Supplier<CompletionStage<Void>> operation) {
        var command = new Command(name);
        command.setDefaultExecutor((sender,context) -> operation.get().exceptionally(error -> { error.printStackTrace(); return null; }));
        MinecraftServer.getCommandManager().register(command);
    }
    private static NpcRenderTransform at(double x) { return new NpcRenderTransform(new NpcTransform(new NpcVector3(x,40,0)),null); }
    public static NpcModel model() {
        var zero = NpcVector3.ZERO;
        var head = new NpcVector3(0,1.4,0);
        var wave = new NpcClip("once",1,Map.of("head",Map.of("rotation",List.of(
            new NpcKeyframe(0,zero,"linear"),new NpcKeyframe(0.5,new NpcVector3(0,0,20),"linear"),new NpcKeyframe(1,zero,"linear")))));
        return new NpcModel("npc_demo:bot",new NpcBounds(new NpcVector3(0,1,0),new NpcVector3(1,2,1)),
            List.of(new NpcBone("body",null,zero,zero),new NpcBone("head","body",head,zero)),
            List.of(new NpcPart("body","npc_demo:body",new NpcVector3(0,0.7,0),zero,zero,new NpcVector3(0.7,1.2,0.4)),
                new NpcPart("head","npc_demo:head",new NpcVector3(0,1.6,0),head,zero,new NpcVector3(0.5,0.5,0.5))),
            Map.of("idle",new NpcClip("loop",1,Map.of()),"wave",wave),Map.of());
    }
}
