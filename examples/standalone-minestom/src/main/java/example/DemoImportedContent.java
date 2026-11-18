package example;

import gg.grounds.npc.core.io.content.NpcContent;
import gg.grounds.npc.core.io.content.NpcContentLoader;
import gg.grounds.npc.core.model.NpcModel;
import gg.grounds.npc.importer.NpcModelImporter;
import gg.grounds.npc.importer.config.NpcImportOptions;
import gg.grounds.npc.importer.config.NpcMinecraftTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Application-owned startup content. Both source import and a precompiled directory end at the same
 * runtime loader and pack verification. Only a temporary source compilation is owned and deleted
 * here.
 */
final class DemoImportedContent implements AutoCloseable {
  private final NpcContent content;
  private final Path pack;
  private final Path work;

  private DemoImportedContent(NpcContent content, Path pack, Path work) {
    this.content = content;
    this.pack = pack;
    this.work = work;
  }

  static DemoImportedContent fromSources(Path source, Path basePack, NpcMinecraftTarget target)
      throws Exception {
    var work = Files.createTempDirectory("npc-original-import-");
    try {
      var options = NpcImportOptions.builder(source, "npc_demo", target);
      if (basePack != null) options.basePack(basePack);
      var root = Files.isDirectory(source) ? source : source.getParent();
      var config = root.resolve("import.json");
      if (Files.isRegularFile(config)) options.config(config);
      var output = NpcModelImporter.compile(options.build(), work.resolve("content"));
      var loaded = load(work.resolve("content"), target);
      return new DemoImportedContent(loaded.content, output.getResourcePack(), work);
    } catch (Exception failure) {
      remove(work);
      throw failure;
    }
  }

  static DemoImportedContent fromPrepared(Path directory, NpcMinecraftTarget target)
      throws Exception {
    return load(directory, target);
  }

  private static DemoImportedContent load(Path directory, NpcMinecraftTarget target)
      throws Exception {
    var content = NpcContentLoader.load(directory.resolve("server-content.zip"));
    if (!content.getTarget().equals(target.getVersion()))
      throw new IllegalArgumentException(
          "Content target differs from the selected runtime profile");
    var pack = directory.resolve("resourcepack.zip");
    content.verifyResourcePack(pack);
    if (!content.getModels().containsKey("npc_demo:npc/bot"))
      throw new IllegalArgumentException("Expected the original bot.bbmodel example");
    return new DemoImportedContent(content, pack, null);
  }

  NpcModel model() {
    return content.getModels().get("npc_demo:npc/bot");
  }

  Path pack() {
    return pack;
  }

  String contentId() {
    return content.getContentId();
  }

  @Override
  public void close() throws Exception {
    if (work != null) remove(work);
  }

  private static void remove(Path path) throws Exception {
    if (Files.exists(path))
      try (var files = Files.walk(path)) {
        for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
      }
  }
}
