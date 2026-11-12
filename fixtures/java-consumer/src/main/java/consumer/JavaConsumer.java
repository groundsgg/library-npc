package consumer;
import gg.grounds.npc.core.*;
import gg.grounds.npc.minestom.*;
import java.util.*;
public final class JavaConsumer {
    public static void main(String[] args) {
        var model = new NpcModel("example:java", new NpcBounds(NpcVector3.ZERO,NpcVector3.ONE),List.of(),
            List.of(new NpcPart(null,"example:cube",NpcVector3.ZERO,NpcVector3.ZERO,NpcVector3.ZERO,NpcVector3.ONE)),Map.of(),Map.of());
        if (NpcPoseSampler.sample(model,null,0.0).getParts().size() != 1) throw new AssertionError();
        var root = new NpcRenderTransform(new NpcTransform(NpcVector3.ZERO),null);
        if (!root.getRoot().getPosition().equals(NpcVector3.ZERO)) throw new AssertionError();
        System.out.println("Java consumer OK: " + model.getId());
    }
}
