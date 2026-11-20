# NPC example server

This optional application runs a small Minestom server with an original bot
model. Use it to try the library or compare its integration with your own code.
For the main walkthrough, start with
[your first NPC in your own server](https://docs.grounds.gg/reference/libraries/npc/getting-started).

## Run it

Use Java 25 and a Minecraft 26.2 client. Configure GitHub Packages access as
explained in [installation](https://docs.grounds.gg/reference/libraries/npc/installation).
From the `library-npc` repository root:

```sh
./gradlew -p examples/standalone-minestom runImportedDemo \
  -PnpcVersion=2.0.0 \
  -PnpcRepository=https://maven.pkg.github.com/groundsgg/library-npc
```

The task imports the included `models/bot.bbmodel`, starts the server on
`localhost:25574`, and serves its pack on port `25575`. Accept the pack when
joining. You should see the bot near `(0, 40, 0)`.

If those ports are occupied, append `-PnpcPort=25584 -PnpcPackPort=25585` and
connect to the chosen game port. Stop with Ctrl+C; the application removes its
NPCs and closes its pack host before stopping Minestom.

## Try its behavior

| Command | Result |
| --- | --- |
| `/npcwave` | The bot waves for all viewers. |
| `/npclook` | Its head turns. |
| `/npcmove` | It moves two blocks along X. |
| `/npcscale` | Only the command sender sees scale and glow changes. |
| `/personal` then `/personalwave` | The sender gets a private wave and sound. |
| `/personalfollow` | The private view follows the shared pose again. |
| `/personalclose` | The private view closes. |
| `/npcremove` | The shared NPC is removed; restart to create it again. |

Use two clients to compare shared and private changes. Shared sound delivery is
silent by default; the private example supplies a recipient-only sound callback.

## Follow the code

- `ImportedNpcDemo` starts the application with source or prepared content.
- `DemoImportedContent` loads models and their matching pack.
- `DemoApplication` creates and publishes the NPC and owns shutdown.
- `SharedNpcPlayback` and `SharedNpcCommands` drive the common NPC.
- `PersonalNpcSessions` owns copies tied to concrete player connections.
- `DemoServer` and `DemoPackHost` provide the surrounding local infrastructure.

These are application examples, not additional library APIs. Use the pieces
that fit your own server. The developer guides explain
[model imports](https://docs.grounds.gg/reference/libraries/npc/importing),
[animation and sound](https://docs.grounds.gg/reference/libraries/npc/shared-npcs),
and [application cleanup](https://docs.grounds.gg/reference/libraries/npc/integration).

## Other tasks and library development

`run` uses a manually constructed demonstration model. `runPersonalDemo` starts
that model with private-presentation commands. `runPrecompiledDemo` loads the
prepared output of the model compiler.

Local publications, the optional Grounds runtime profile, synthetic smoke tests
and artifact-origin checks are advanced workflows. See the
[advanced guide](https://docs.grounds.gg/reference/libraries/npc/advanced) and this
example's `build.gradle.kts` for their task definitions.
