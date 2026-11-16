package consumer;

import gg.grounds.npc.minestom.NpcHandle;
import gg.grounds.npc.minestom.presentation.NpcPresentationHandle;
import gg.grounds.npc.minestom.presentation.NpcPresentationOptions;
import gg.grounds.npc.minestom.presentation.NpcPresentationResult;
import gg.grounds.npc.minestom.presentation.NpcPresentationStart;
import gg.grounds.npc.minestom.presentation.NpcPresentations;
import gg.grounds.npc.minestom.transform.NpcRenderTransform;

import gg.grounds.npc.core.animation.NpcPoseSampler;
import gg.grounds.npc.core.math.NpcTransform;
import gg.grounds.npc.core.math.NpcVector3;
import gg.grounds.npc.core.model.NpcBounds;
import gg.grounds.npc.core.model.NpcModel;
import gg.grounds.npc.core.model.NpcPart;
import gg.grounds.npc.minestom.*;
import java.util.*;
public final class JavaConsumer {
    // Compiled against Maven artifacts, with no composite or implementation imports.
    public static java.util.concurrent.CompletionStage<Void> gesture(NpcHandle parent, net.minestom.server.entity.Player player) {
        NpcPresentationStart start = NpcPresentations.prepare(parent,NpcPresentationOptions.builder(player).build());
        return start.ready().thenCompose(result -> {
            if(!(result instanceof NpcPresentationResult.Ready ready)) return start.cancel();
            NpcPresentationHandle handle=ready.getHandle();
            return handle.publish().thenCompose(v -> handle.playClip("wave")).thenCompose(v -> handle.followSharedHeadLook())
                .handle((v,error) -> start.cancel().thenCompose(ignored -> error == null
                    ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                    : java.util.concurrent.CompletableFuture.<Void>failedFuture(error)))
                .thenCompose(next -> next);
        });
    }
    public static void main(String[] args) {
        var model = new NpcModel("example:java", new NpcBounds(NpcVector3.ZERO,NpcVector3.ONE),List.of(),
            List.of(new NpcPart(null,"example:cube",NpcVector3.ZERO,NpcVector3.ZERO,NpcVector3.ZERO,NpcVector3.ONE)),Map.of(),Map.of());
        if (NpcPoseSampler.sample(model,null,0.0).getParts().size() != 1) throw new AssertionError();
        var root = new NpcRenderTransform(new NpcTransform(NpcVector3.ZERO),null);
        if (!root.getRoot().getPosition().equals(NpcVector3.ZERO)) throw new AssertionError();
        System.out.println("Java consumer OK: " + model.getId());
    }
}
