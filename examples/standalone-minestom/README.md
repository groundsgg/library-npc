# NPC examples

Start with `NpcDemo.java`: one shared NPC, an explicitly host-driven clip clock and complete asynchronous cleanup. `PersonalNpcDemo.java` adds private clip/head/sound channels for each concrete player connection. Server setup, model construction, commands and resource-pack hosting are named helpers; synthetic players and assertions live only in `src/smoke`.

Build and run from the library root with JDK 25:

```sh
./gradlew publishAllPublicationsToReviewRepository
./gradlew -p examples/standalone-minestom run -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p examples/standalone-minestom runPersonalDemo -PnpcRepository="file:$PWD/.evidence/maven"
```

The independent build reads the version from `../../version.txt`; `-PnpcVersion=...` selects another candidate. It resolves Maven artifacts, never library sources. Defaults are game port 25574 and pack port 25575. Override with `-PnpcPort=... -PnpcPackPort=...` when another demo owns those ports. Accept the neutral demo resource pack in your client.

The shared example offers `/npcwave`, `/npcidle`, `/npclook`, `/npcscale`, `/npcmove` and `/npcremove`. Its playback helper owns the repeating advance task; removal cancels the task and drains the NPC.

In the personal example, A and B can each `/personal`. Only A sees/hears `/personalwave` from A; only B sees `/personallook` from B. Other players retain the shared NPC. `/personalfollow` releases both private channels, `/personalclose` restores the latest common pose. `/personalidle` and `/personalstop` retain private ownership without replaying sound.

For automated checks:

```sh
./gradlew -p examples/standalone-minestom test smoke -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p examples/standalone-minestom smokeGroundsProfile -PnpcRepository="file:$PWD/.evidence/maven" -PgroundsClasspathFile=/absolute/path/to/classpath.local
```

Smoke tasks use ephemeral ports and a separate synthetic A/B/observer harness. The Grounds task requires the complete matching 26.3 runtime classpath and uses the matching resource pack. `runGroundsProfile` runs the interactive shared example on that runtime.

Smoke success proves server-side startup, channels, movement and cleanup. Visual/audio acceptance with real clients, licensed content and actual backend/proxy transfers remain separate checks. No live handle, Entity ID or connection-owned presentation may be carried to another backend.
