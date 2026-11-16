package example;

import gg.grounds.npc.minestom.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Run the shared NPC with optional private presentations for individual player connections.
 *
 * <p>The extra commands are installed by {@link PersonalNpcCommands}; {@link PersonalNpcSessions}
 * retains each presentation's cancellation owner. Private clip and head channels initially follow
 * the shared NPC. Each command takes ownership of only the channel it changes.
 */
public final class PersonalNpcDemo {
  public static void main(String[] args) throws Exception {
    var demo = DemoApplication.start(DemoServer.start(false), true);
    Runtime.getRuntime().addShutdownHook(new Thread(demo::close, "npc-personal-demo-shutdown"));
    System.out.println(
        "Personal NPC: /personal /personalwave /personallook /personalfollow /personalclose");
  }
}
