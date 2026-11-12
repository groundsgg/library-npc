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
        var pack = packFile == null ? null : new DemoPackHost(java.nio.file.Path.of(packFile),smoke ? 0 : Integer.getInteger("npc.packPort",25575));
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
        server.start("127.0.0.1",smoke ? 0 : Integer.getInteger("npc.port",25574));
        try {
            var prepared = start.ready().toCompletableFuture().get(5,TimeUnit.SECONDS);
            if (!(prepared instanceof NpcPrepareResult.Ready ready)) throw new IllegalStateException(prepared.toString());
            var npc = ready.getHandle();
            npc.publish().toCompletableFuture().get(5,TimeUnit.SECONDS);
            var personal = new NpcDemoPersonal(npc,player -> new NpcPresentationOptions(player,
                operation -> {operation.run();return true;},
                (recipient,origin,sound) -> recipient.playSound(net.kyori.adventure.sound.Sound.sound(
                    net.kyori.adventure.key.Key.key(sound.getKey()),net.kyori.adventure.sound.Sound.Source.MASTER,
                    (float)sound.getVolume(),(float)sound.getPitch()))));
            playerCommand("personal",p -> personal.open(p).thenApply(h -> null));
            playerCommand("personalwave",p -> personal.with(p,h -> h.playClip("wave")));
            playerCommand("personalidle",p -> personal.with(p,h -> h.playClip("idle")));
            playerCommand("personalstop",p -> personal.with(p,NpcPresentationHandle::stopClip));
            playerCommand("personallook",p -> personal.with(p,h -> h.applyHeadLook(35,-15)));
            playerCommand("personalfollow",p -> personal.with(p,h -> h.followSharedClip().thenCompose(v -> h.followSharedHeadLook())));
            playerCommand("personalclose",personal::close);
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
                var a = smokePlayer(instance); var b = smokePlayer(instance); var observer = smokePlayer(instance);
                var privateA = personal.open(a).toCompletableFuture().get(5,TimeUnit.SECONDS);
                var privateB = personal.open(b).toCompletableFuture().get(5,TimeUnit.SECONDS);
                personal.with(a,h -> h.playClip("wave")).toCompletableFuture().get(5,TimeUnit.SECONDS);
                personal.with(b,h -> h.applyHeadLook(-30,10)).toCompletableFuture().get(5,TimeUnit.SECONDS);
                if(privateA.snapshot().getClipMode()!=NpcPresentationChannel.PRIVATE || privateB.snapshot().getClipMode()!=NpcPresentationChannel.FOLLOW_SHARED)
                    throw new AssertionError("Independent clip channels");
                if(instance.getEntities().stream().filter(e -> e.getEntityType()==EntityType.ITEM_DISPLAY).count()!=6)
                    throw new AssertionError("Common plus A/B groups");
                personal.close(a).toCompletableFuture().get(5,TimeUnit.SECONDS);
                personal.close(b).toCompletableFuture().get(5,TimeUnit.SECONDS);
                a.remove(); b.remove(); observer.remove();
                playback.start("wave").toCompletableFuture().get(5,TimeUnit.SECONDS);
                npc.applyHeadLook(20,-10).toCompletableFuture().get(5,TimeUnit.SECONDS);
                npc.applyTransform(at(512)).toCompletableFuture().get(5,TimeUnit.SECONDS);
                if (instance.getEntities().stream().filter(e -> e.getEntityType() == EntityType.ITEM_DISPLAY).count() != 2)
                    throw new AssertionError("Expected two parts");
                playback.remove().toCompletableFuture().get(5,TimeUnit.SECONDS);
                if (instance.getEntities().stream().anyMatch(e -> e.getEntityType() == EntityType.ITEM_DISPLAY))
                    throw new AssertionError("Leaked display");
                System.out.println("NPC standalone smoke OK: spawn, A/B private channels, private close, wave, headlook, move, close");
            } else System.out.println("NPC demo on localhost:" + Integer.getInteger("npc.port",25574) + ". Accept the original demo pack. Shared: /npcwave /npcidle /npclook /npcscale /npcmove /npcremove. Personal: /personal /personalwave /personalidle /personalstop /personallook /personalfollow /personalclose");
        } finally { if (smoke) { start.cancel().toCompletableFuture().get(5,TimeUnit.SECONDS); if (pack != null) pack.close(); MinecraftServer.stopCleanly(); } }
    }
    private static net.minestom.server.entity.Player smokePlayer(net.minestom.server.instance.Instance instance) {
        var connection = new net.minestom.server.network.player.PlayerConnection() {
            @Override public void sendPacket(net.minestom.server.network.packet.server.SendablePacket packet) {}
            @Override public java.net.SocketAddress getRemoteAddress() {return new java.net.InetSocketAddress(0);}
        };
        var player = new net.minestom.server.entity.Player(connection,new net.minestom.server.network.player.GameProfile(UUID.randomUUID(),"smoke"));
        player.setInstance(instance,new Pos(0,40,4)).join();return player;
    }
    private static void playerCommand(String name,java.util.function.Function<net.minestom.server.entity.Player,CompletionStage<Void>> operation) {
        var command = new Command(name);
        command.setDefaultExecutor((sender,context) -> {
            if(sender instanceof net.minestom.server.entity.Player player) operation.apply(player).exceptionally(error -> {
                player.sendMessage("NPC demo: " + error.getMessage());return null;
            });
        });
        MinecraftServer.getCommandManager().register(command);
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
            Map.of("idle",new NpcClip("loop",1,Map.of()),"wave",wave),Map.of("wave",new NpcClipSound("minecraft:entity.villager.yes",0.8,1.0,12)));
    }
}
