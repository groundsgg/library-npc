package example;

import gg.grounds.npc.core.animation.NpcBoneTrack;
import gg.grounds.npc.core.animation.NpcClip;
import gg.grounds.npc.core.animation.NpcInterpolation;
import gg.grounds.npc.core.animation.NpcKeyframe;
import gg.grounds.npc.core.animation.NpcLoopMode;
import gg.grounds.npc.core.math.NpcTransform;
import gg.grounds.npc.core.math.NpcVector3;
import gg.grounds.npc.core.model.NpcBone;
import gg.grounds.npc.core.model.NpcBounds;
import gg.grounds.npc.core.model.NpcClipSound;
import gg.grounds.npc.core.model.NpcModel;
import gg.grounds.npc.core.model.NpcPart;
import gg.grounds.npc.minestom.*;
import gg.grounds.npc.minestom.transform.NpcRenderTransform;
import java.util.*;
import java.util.concurrent.*;

/** Original neutral two-bone content: one loop and one waving gesture. */
final class DemoNpcModel {
  private DemoNpcModel() {}

  static NpcModel create() {
    var zero = NpcVector3.ZERO;
    var head = new NpcVector3(0, 1.4, 0);
    var wave =
        new NpcClip(
            NpcLoopMode.ONCE,
            1,
            Map.of(
                "head",
                new NpcBoneTrack(
                    List.of(),
                    List.of(
                        new NpcKeyframe(0, zero, NpcInterpolation.LINEAR),
                        new NpcKeyframe(0.5, new NpcVector3(0, 0, 20), NpcInterpolation.LINEAR),
                        new NpcKeyframe(1, zero, NpcInterpolation.LINEAR)),
                    List.of())));
    return new NpcModel(
        "npc_demo:bot",
        new NpcBounds(new NpcVector3(0, 1, 0), new NpcVector3(1, 2, 1)),
        List.of(new NpcBone("body", null, zero, zero), new NpcBone("head", "body", head, zero)),
        List.of(
            new NpcPart(
                "body",
                "npc_demo:body",
                new NpcVector3(0, 0.7, 0),
                zero,
                zero,
                new NpcVector3(0.7, 1.2, 0.4)),
            new NpcPart(
                "head",
                "npc_demo:head",
                new NpcVector3(0, 1.6, 0),
                head,
                zero,
                new NpcVector3(0.5, 0.5, 0.5))),
        Map.of("idle", new NpcClip(NpcLoopMode.LOOP, 1, Map.of()), "wave", wave),
        Map.of("wave", new NpcClipSound("minecraft:entity.villager.yes", 0.8, 1.0, 12)));
  }

  static NpcRenderTransform at(double x) {
    return new NpcRenderTransform(new NpcTransform(new NpcVector3(x, 40, 0)), null);
  }
}
