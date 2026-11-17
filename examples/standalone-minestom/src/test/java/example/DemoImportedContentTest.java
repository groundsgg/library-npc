package example;

import static org.junit.jupiter.api.Assertions.*;

import gg.grounds.npc.importer.NpcModelImporter;
import gg.grounds.npc.importer.config.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoImportedContentTest {
  @TempDir Path temp;

  @Test
  void startupAndPrecompiledModesUseTheSameModelAndPackContract() throws Exception {
    var sources = Path.of(System.getProperty("npc.importSources"));
    var base = Path.of(System.getProperty("npc.importBase"));
    try (var startup =
        DemoImportedContent.fromSources(sources, base, NpcMinecraftTarget.MINECRAFT_26_2)) {
      assertEquals(2, startup.model().getParts().size());
      assertEquals("npc_demo:npc/bot", startup.model().getId());
      assertTrue(startup.model().getAnimations().containsKey("wave"));
      var output =
          NpcModelImporter.compile(
              NpcImportOptions.builder(sources, "npc_demo", NpcMinecraftTarget.MINECRAFT_26_2)
                  .basePack(base)
                  .config(sources.resolve("import.json"))
                  .build(),
              temp.resolve("prepared"));
      try (var prepared =
          DemoImportedContent.fromPrepared(
              temp.resolve("prepared"), NpcMinecraftTarget.MINECRAFT_26_2)) {
        assertEquals(startup.contentId(), prepared.contentId());
        assertArrayEquals(Files.readAllBytes(startup.pack()), Files.readAllBytes(prepared.pack()));
      }
      assertTrue(Files.exists(output.getServerContent()));
    }
  }

  @Test
  void precompiledModeRejectsAMissingOrMismatchedTarget() throws Exception {
    assertThrows(
        Exception.class,
        () ->
            DemoImportedContent.fromPrepared(
                temp.resolve("missing"), NpcMinecraftTarget.MINECRAFT_26_2));
    var sources = Path.of(System.getProperty("npc.importSources"));
    NpcModelImporter.compile(
        NpcImportOptions.builder(sources, "npc_demo", NpcMinecraftTarget.MINECRAFT_26_3).build(),
        temp.resolve("prepared"));
    assertThrows(
        Exception.class,
        () ->
            DemoImportedContent.fromPrepared(
                temp.resolve("prepared"), NpcMinecraftTarget.MINECRAFT_26_2));
  }
}
