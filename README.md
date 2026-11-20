# NPC library

Create animated NPCs in your own Minestom server, using Java or Kotlin. Load a
prepared model, place it in a world, and add shared or per-player behavior.
The library does not require Grounds services.

**[Create your first NPC](https://docs.grounds.gg/reference/libraries/npc/getting-started)**
uses a supplied model and resourcepack in your existing server. You do not need
to build this repository or learn the importer first.

## Developer documentation

The [NPC documentation](https://docs.grounds.gg/reference/libraries/npc/index)
is the main usage guide:

- [Install the published library](https://docs.grounds.gg/reference/libraries/npc/installation).
- [Import your own Blockbench model](https://docs.grounds.gg/reference/libraries/npc/importing).
- [Add animation, sound and movement](https://docs.grounds.gg/reference/libraries/npc/shared-npcs).
- [Change an NPC for one player](https://docs.grounds.gg/reference/libraries/npc/personal-presentations).
- [Manage NPC lifetimes in your application](https://docs.grounds.gg/reference/libraries/npc/integration).
- [Troubleshoot an integration](https://docs.grounds.gg/reference/libraries/npc/troubleshooting).

NPC **2.0.0** is published to GitHub Packages. Use the same version for all NPC
modules you select. `npc-minestom` includes core transitively; prepared-content
loading needs neither the importer nor CLI. See installation for repository
access and the supported Minestom version.

## Complete example application

[`examples/standalone-minestom`](examples/standalone-minestom) is an optional
server to run and inspect alongside your own integration. Its README explains
how to launch it and where the application code lives.

## Library development

Use Java 25. From this checkout:

```sh
./gradlew check apiCheck dokkaGenerate
```

See [advanced workflows](https://docs.grounds.gg/reference/libraries/npc/advanced)
for local Maven builds, independent consumers and verification tools.
[API contracts](docs/api.md), [compatibility](docs/compatibility.md) and
[publishing](docs/publishing.md) document maintainer details.
Public modules use the MIT license; see [LICENSE](LICENSE) and [NOTICE](NOTICE).
