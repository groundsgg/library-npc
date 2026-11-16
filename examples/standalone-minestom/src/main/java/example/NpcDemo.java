package example;

import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Run the shared scenario: every admitted player sees the same clip and head pose.
 *
 * <p>Read {@link DemoApplication#start} next for prepare/ready/publish and failure cleanup, then
 * {@link SharedNpcPlayback} for the host-owned animation clock. {@link DemoServer} contains only
 * the local server and resource-pack setup. See the example README for commands and expected
 * results.
 */
public final class NpcDemo {
  public static void main(String[] args) throws Exception {
    var demo = DemoApplication.start(DemoServer.start(false), false);
    Runtime.getRuntime().addShutdownHook(new Thread(demo::close, "npc-demo-shutdown"));
    System.out.println("Shared NPC: /npcwave /npcidle /npclook /npcscale /npcmove /npcremove");
  }
}
