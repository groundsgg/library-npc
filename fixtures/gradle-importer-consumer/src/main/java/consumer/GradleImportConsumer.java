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
    var work = java.nio.file.Files.createTempDirectory("independent-npc-api-");
    try {
      var options =
          gg.grounds.npc.importer.config.NpcImportOptions.builder(
                  Path.of(args[1]),
                  "example",
                  gg.grounds.npc.importer.config.NpcMinecraftTarget.MINECRAFT_26_3)
              .build();
      var imported =
          gg.grounds.npc.importer.NpcModelImporter.compile(options, work.resolve("content"));
      var direct = NpcContentLoader.load(imported.getServerContent());
      direct.verifyResourcePack(imported.getResourcePack());
      if (!content.getContentId().equals(direct.getContentId()))
        throw new IllegalStateException("API and CLI content differ");
    } finally {
      try (var files = java.nio.file.Files.walk(work)) {
        for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList())
          java.nio.file.Files.delete(path);
      }
    }
    System.out.println("NPC_CONTENT " + content.getContentId());
    for (var type :
        new Class<?>[] {
          NpcContentLoader.class,
          gg.grounds.npc.importer.NpcModelImporter.class,
          gg.grounds.npc.cli.NpcCli.class
        }) {
      String module =
          type == NpcContentLoader.class
              ? "npc-core"
              : type == gg.grounds.npc.importer.NpcModelImporter.class ? "npc-importer" : "npc-cli";
      System.out.println(
          "NPC_ORIGIN "
              + module
              + " "
              + Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }
  }
}
