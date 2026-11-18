# NPC examples

These small Minestom applications demonstrate how to own an NPC from preparation through shutdown.
Start with the shared scenario, then use two clients to explore private clip, head-look and sound
channels. Both scenarios use the same neutral two-part model and resource pack.

These examples use released NPC `1.1.0`, including the importer and CLI. The rendering lifecycle retains the 1.0 API. Use the older release-tag examples with `0.2.0`.

## Import an original Blockbench model

`models/bot.bbmodel` is original freely distributable source: a blue body, orange head, idle clip and one-second head wave. `models/import.json` declares its external Vanilla sound cue. `base-pack/` is a small independent pack used to demonstrate merging. Purchased models and a Grounds checkout are unnecessary.

Use JDK 25 and configure [GitHub Packages credentials](../../docs/installation.md), then select the release:

```sh
NPC_VERSION=1.1.0
NPC_REPOSITORY=https://maven.pkg.github.com/groundsgg/library-npc
```

Choose either loading path:

```sh
# Import the .bbmodel through the library API before accepting players.
./gradlew -p examples/standalone-minestom runImportedDemo \
  -PnpcVersion="$NPC_VERSION" -PnpcRepository="$NPC_REPOSITORY"

# Compile through the published CLI in Gradle, then load only its prepared files.
./gradlew -p examples/standalone-minestom runPrecompiledDemo \
  -PnpcVersion="$NPC_VERSION" -PnpcRepository="$NPC_REPOSITORY"
```

Run one application at a time. Connect to `localhost:25574`, accept its pack and try `/npcwave`, `/npclook` and `/npcprivate`. Both modes use the same lifecycle/commands. `runPrecompiledDemo` first runs `compileDemoNpc`, whose generated output is `build/imported/26.2/`. The API startup mode owns a temporary compilation directory and removes it after its pack host and NPCs close. Editing the source requires restarting.

Read `ImportedNpcDemo.java` for bootstrap ownership, then `DemoImportedContent.java` for source versus prepared loading, then `DemoApplication.java` for the existing rendering lifecycle. Pack generation is done by the importer, and hosting/sending remains example code.

For the actual Grounds runtime profile, use `runImportedGroundsDemo` or `runPrecompiledGroundsDemo` with `-PgroundsClasspathFile=/absolute/path/to/classpath.local`. The target becomes 26.3 and its metadata uses 97.1. Synthetic verification uses `smokeImported`, `smokePrecompiled`, `smokeImportedGrounds` and `smokePrecompiledGrounds` on temporary ports.

The complete [import guide](../../docs/importing.md) explains standalone CLI/JAR usage, base-pack conflicts, Java/Kotlin APIs, target selection and format limits. To retain only prepared runtime dependencies, copy the loader calls into your application and keep `npc-importer`/`npc-cli` in its build pipeline.

When editing the library, select a separate local candidate before running the same tasks:

```sh
NPC_VERSION="$(cat version.txt)-local-SNAPSHOT"
NPC_REPOSITORY="file:$PWD/.evidence/maven"
./gradlew -PversionOverride="$NPC_VERSION" publishAllPublicationsToReviewRepository :npc-cli:executableJar
```

## Run the shared example

Use JDK 25 and run these commands from the `library-npc` root:

```sh
NPC_VERSION="$(cat version.txt)-local-SNAPSHOT"
./gradlew -PversionOverride="$NPC_VERSION" publishAllPublicationsToReviewRepository
./gradlew -p examples/standalone-minestom run -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven"
```

Connect to `localhost:25574` and accept the neutral demo resource pack served on port `25575`.
You should see a body and head near `(0, 40, 0)`. The server freezes world lighting; NPC animation
still advances through its separate host-owned clock.

The independent Gradle build resolves Maven artifacts. Without an override it reads `../../version.txt`.
To copy this example outside the repository, supply both an absolute repository URI and a version:

```sh
./gradlew -p /absolute/path/to/copied-example run \
  -PnpcVersion="$NPC_VERSION" \
  -PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven
```

If another demo owns the default ports, append `-PnpcPort=25584 -PnpcPackPort=25585` to the run command.
Use the matching client/runtime profile described below.

## Read the code in this order

| File                                                                                                                       | What to learn                                                                                        |
| -------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| [`NpcDemo.java`](src/main/java/example/NpcDemo.java)                                                                       | Start the application and register its shutdown owner.                                               |
| [`DemoApplication.java`](src/main/java/example/DemoApplication.java)                                                       | Retain `NpcStart`, handle readiness, publish hidden parts, and drain cleanup on failure or shutdown. |
| [`DemoNpcModel.java`](src/main/java/example/DemoNpcModel.java)                                                             | Define parent-before-child bones, item-model IDs, bounds, typed clips, keyframes and sound cues.     |
| [`SharedNpcPlayback.java`](src/main/java/example/SharedNpcPlayback.java)                                                   | Drive absolute clip-local milliseconds and stop the repeating task on removal.                       |
| [`SharedNpcCommands.java`](src/main/java/example/SharedNpcCommands.java)                                                   | Observe command stages, move the root, and apply a connection-specific scale/glow override.          |
| [`PersonalNpcCommands.java`](src/main/java/example/PersonalNpcCommands.java)                                               | Request private channels and route sounds exclusively to their recipient.                            |
| [`PersonalNpcSessions.java`](src/main/java/example/PersonalNpcSessions.java)                                               | Retain cancellation tokens across asynchronous preparation and reconnects.                           |
| [`DemoServer.java`](src/main/java/example/DemoServer.java), [`DemoPackHost.java`](src/main/java/example/DemoPackHost.java) | Local server, world and pack hosting infrastructure; read these after the NPC lifecycle.             |

Synthetic players and assertions live separately in `src/smoke`; they are not part of the interactive application.

## Follow one shared NPC through its lifetime

1. `DemoNpcModel.create()` constructs an immutable numeric definition. The body is the parent of
   the head; `NpcPart` item-model IDs point at resources in `pack/`. Core construction validates
   the model before any Minestom entities exist.
2. `DemoApplication.start()` builds `NpcSpawnRequest` from the owning instance, model and root
   transform. `NpcRenderer.prepare()` returns an `NpcStart` immediately; retain it before observing
   `ready()` so failed or unfinished preparation can still be cancelled.
3. A `Ready` result contains a hidden handle. Awaiting `publish()` is the separate step that admits
   viewers. A `Rejected` result contains diagnostics and does not provide a usable handle.
4. `SharedNpcPlayback` advances the shared clip by 50 ms per nominal server tick. Calling
   `playClip` alone does not install an animation clock. Starting another clip resets this local
   elapsed value to zero.
5. Removal cancels the host's advance task, then observes `closeAsync()`. Application shutdown also
   awaits `start.cancel()` before stopping Minestom, including pending private presentations and
   accepted sound or movement work.

The demo's five-second blocking `await` helper runs on bootstrap/shutdown threads. In production,
compose `CompletionStage` results into your host lifecycle. Do not block an instance-tick callback
waiting for work queued onto that same instance scheduler. Retain the start token, keep observing
failures, and include cancellation in your host's resource drain.

### Time and pose units

| Value                                                                     | Unit and meaning                                                                                                   |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `NpcKeyframe.timeSeconds`, `NpcClip.lengthSeconds`, numeric pose sampling | Seconds. The wave lasts `1.0` second and reaches its peak at `0.5`.                                                |
| Shared `playClip(id, elapsedMillis)` and `advanceClipTo(elapsedMillis)`   | Absolute clip-local milliseconds: `50`, `100`, `150`...; passing `50` repeatedly keeps seeking to the same sample. |
| Head yaw/pitch and authored bone rotations                                | Degrees. The demo's wave rotates the head around its local Z axis.                                                 |
| Root placement                                                            | World coordinates. `DemoNpcModel.at(2)` moves the root to `(2, 40, 0)`.                                            |

The shared clock uses tick time, so animation slows with a lagging server. Personal presentations
instead use the library's monotonic clock and need no host `advanceClipTo` task.

## Try the shared commands

| Command      | Expected result                                                                                             |
| ------------ | ----------------------------------------------------------------------------------------------------------- |
| `/npcwave`   | Every admitted viewer sees the one-second head gesture, starting at clip time zero.                         |
| `/npcidle`   | The shared NPC returns to its idle clip; its empty tracks preserve the rest pose.                           |
| `/npclook`   | The shared head receives additive yaw/pitch without rotating the whole body.                                |
| `/npcscale`  | Only the calling player's connection sees scale `1.3` and glow. Another viewer keeps the normal appearance. |
| `/npcmove`   | The shared root moves to `(2, 40, 0)`; owned private copies follow the parent.                              |
| `/npcremove` | The advance task stops and the NPC drains cleanup. Restart the application to create it again.              |

The model declares a `wave` sound cue, but the shared request uses the default silent sound sink.
The personal example below injects an exclusive recipient sink so you can hear its private cue.

## Try a personal presentation with two clients

Stop the shared application before using the same ports, then run:

```sh
./gradlew -p examples/standalone-minestom runPersonalDemo -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven"
```

1. Connect A and B and accept the pack on both clients. Both initially see the common NPC.
2. A runs `/personal`. The private visual replaces the common NPC only for A and initially follows
   both shared channels. B keeps seeing the common NPC.
3. A runs `/personalwave`. Only A's clip channel becomes private; A sees the wave and hears one
   cue. B neither sees that private gesture nor hears its cue.
4. A runs `/personallook`. A's head channel is now private as well. Shared root movement and
   A's scale/glow override still apply without restarting those private channels.
5. A runs `/personalstop` or `/personalidle`. The clip remains private, in rest or idle. Stopping
   does not mean following the shared clip again.
6. A runs `/personalfollow`. Both channels resume the parent's current shared pose; the personal
   presentation remains allocated. `/personalclose` removes it and restores the shared visual.

B can also open a presentation and use the same commands independently. A repeated `/personal`
on an already open session fails; close it first. Reconnecting creates a new `Player` connection
and new ownership, even with the same UUID. Do not transfer handles or viewer overrides between
connections, backends or proxies.

## Runtime profiles and automated verification

The default interactive tasks use public Minestom `2026.10.07-26.2`. For the Grounds
`2026.11.08-26.3-grounds` runtime, provide the complete matching JAR classpath and run:

```sh
./gradlew -p examples/standalone-minestom runGroundsProfile \
  -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven" \
  -PgroundsClasspathFile=/absolute/path/to/classpath.local
```

`runGroundsProfile` runs the shared scenario with the matching 26.3 pack. The classpath file contains
the complete runtime JAR paths separated by the platform's path separator. It is a local profile,
not a replacement for the Maven-resolved NPC library.

To run the independent example tests and both synthetic smoke profiles:

```sh
./gradlew -p examples/standalone-minestom test smoke -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven"
./gradlew -p examples/standalone-minestom smokeGroundsProfile \
  -PnpcVersion="$NPC_VERSION" -PnpcRepository="file:$PWD/.evidence/maven" \
  -PgroundsClasspathFile=/absolute/path/to/classpath.local
```

Smoke tasks choose ephemeral ports and exercise spawn, A/B/observer isolation, private cleanup,
wave, head look, movement and final removal. Expect `NPC standalone smoke OK` before the server
shuts down. Real client visuals/audio, licensed-content and actual backend/proxy transfer acceptance
remain separate checks.

## Troubleshooting

| Symptom                                                   | Check                                                                                                                                       |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| No model geometry or missing-texture items                | Accept the pack; verify the pack port is available and the runtime uses its matching pack.                                                  |
| Shared clip starts but does not progress in your own host | Install an advance driver and pass increasing absolute milliseconds.                                                                        |
| No sound in the shared example                            | The default sink is silent. Supply `NpcSoundSink` in your host, or use the personal example's recipient sink.                               |
| Blocking timeout during startup integration               | Do not invoke the demo's blocking `await` from an instance tick. Compose stages instead.                                                    |
| Old packages or methods while compiling                   | Match the candidate version and selected Maven repository. After rebuilding a mutable local candidate, use Gradle `--refresh-dependencies`. |

Continue with the library's [API contracts](../../docs/api.md) and [migration guide](../../docs/migration.md).
