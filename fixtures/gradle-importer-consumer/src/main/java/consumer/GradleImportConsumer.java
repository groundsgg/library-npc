package consumer;

import gg.grounds.npc.core.io.content.NpcContentLoader;
import java.nio.file.Path;

/** Verifies the published CLI's output using only the separately resolved runtime artifact. */
public final class GradleImportConsumer {
  public static void main(String[] args) throws Exception {
    var output = Path.of(args[0]);
    var content = NpcContentLoader.load(output.resolve("server-content.zip"));
    content.verifyResourcePack(output.resolve("resourcepack.zip"));
    if (!content.getModels().containsKey("example:npc/cube")) {
      throw new IllegalStateException("Compiled model did not load");
    }
    System.out.println("NPC_CONTENT " + content.getContentId());
    for (var type : new Class<?>[] {NpcContentLoader.class}) {
      System.out.println(
          "NPC_ORIGIN npc-core "
              + Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }
  }
}
