package example;

import gg.grounds.npc.importer.config.NpcMinecraftTarget;
import java.nio.file.Path;

/** Exercises the real lifecycle against imported original assets, on temporary ports only. */
public final class ImportedNpcDemoSmoke {
  public static void main(String[] args) throws Exception {
    var target = NpcMinecraftTarget.fromVersion(System.getProperty("npc.importTarget", "26.2"));
    String prepared = System.getProperty("npc.precompiledContent");
    try (var content =
        prepared == null
            ? DemoImportedContent.fromSources(
                Path.of(System.getProperty("npc.importSources")),
                Path.of(System.getProperty("npc.importBase")),
                target)
            : DemoImportedContent.fromPrepared(Path.of(prepared), target)) {
      System.setProperty("npc.demoPack", content.pack().toString());
      try (var demo = DemoApplication.start(DemoServer.start(true), true, content.model())) {
        NpcDemoSmoke.exercise(demo);
        for (var entry :
            java.util.Map.of(
                    "npc-core", gg.grounds.npc.core.io.content.NpcContentLoader.class,
                    "npc-importer", gg.grounds.npc.importer.NpcModelImporter.class,
                    "npc-minestom", gg.grounds.npc.minestom.NpcRenderer.class)
                .entrySet()) {
          System.out.println(
              "NPC_ORIGIN "
                  + entry.getKey()
                  + " "
                  + java.nio.file.Path.of(
                      entry
                          .getValue()
                          .getProtectionDomain()
                          .getCodeSource()
                          .getLocation()
                          .toURI()));
        }

        System.out.println(
            "NPC imported smoke OK mode="
                + (prepared == null ? "startup" : "precompiled")
                + " target="
                + target.getVersion()
                + " contentId="
                + content.contentId());
      }
    }
  }
}
