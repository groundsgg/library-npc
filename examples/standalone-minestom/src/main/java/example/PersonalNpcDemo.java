package example;

import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;

/** Add per-connection clip, head-look and sound channels to the shared NPC example. */
public final class PersonalNpcDemo {
  public static void main(String[] args) throws Exception {
    var demo = DemoApplication.start(DemoServer.start(false), true);
    Runtime.getRuntime().addShutdownHook(new Thread(demo::close, "npc-personal-demo-shutdown"));
    System.out.println(
        "Personal NPC: /personal /personalwave /personallook /personalfollow /personalclose");
  }
}
