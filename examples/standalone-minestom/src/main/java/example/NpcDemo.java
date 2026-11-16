package example;

import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;

/** Start here: a shared NPC with an explicitly host-driven clip clock. */
public final class NpcDemo {
  public static void main(String[] args) throws Exception {
    var demo = DemoApplication.start(DemoServer.start(false), false);
    Runtime.getRuntime().addShutdownHook(new Thread(demo::close, "npc-demo-shutdown"));
    System.out.println("Shared NPC: /npcwave /npcidle /npclook /npcscale /npcmove /npcremove");
  }
}
