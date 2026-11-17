package example;

import gg.grounds.npc.importer.config.NpcMinecraftTarget;
import java.nio.file.Path;

/**
 * Interactive original Blockbench model example. Select source import or precompiled content in the
 * Gradle entrypoint; both feed the existing shared/personal NPC lifecycle and commands.
 */
public final class ImportedNpcDemo {
  public static void main(String[] args) throws Exception {
    var target = NpcMinecraftTarget.fromVersion(System.getProperty("npc.importTarget", "26.2"));
    String prepared = System.getProperty("npc.precompiledContent");
    var content =
        prepared == null
            ? DemoImportedContent.fromSources(
                Path.of(System.getProperty("npc.importSources")),
                Path.of(System.getProperty("npc.importBase")),
                target)
            : DemoImportedContent.fromPrepared(Path.of(prepared), target);
    try {
      System.setProperty("npc.demoPack", content.pack().toString());
      var demo = DemoApplication.start(DemoServer.start(false), true, content.model());
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      demo.close();
                    } finally {
                      try {
                        content.close();
                      } catch (Exception failure) {
                        failure.printStackTrace();
                      }
                    }
                  },
                  "npc-import-demo-shutdown"));
      System.out.println(
          "Imported NPC contentId=" + content.contentId() + " pack=" + content.pack());
      System.out.println("Use /npcwave /npcidle /npclook /npcmove and /npcprivate.");
    } catch (Exception failure) {
      try {
        content.close();
      } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }
}
