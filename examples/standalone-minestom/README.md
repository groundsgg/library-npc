# Personal NPC demo

Build the library to a selected local Maven repository, then run this independent
Java project with `-PnpcRepository=file:///absolute/path/to/library-npc/.evidence/maven`.
An explicit `-PnpcVersion=...` selects a candidate; defaults match the public CI
candidate. No composite build or product assets are used.

`run` starts public Minestom. `runGroundsProfile` requires the complete existing
26.3 classpath through `-PgroundsClasspathFile=/absolute/path/to/classpath.local`.
The latter uses the neutral 26.3 resource pack. Defaults are game25574/pack25575;
use `-PnpcPort=25576 -PnpcPackPort=25577` to preserve an existing demo.
`run --args=--smoke` (or `runGroundsProfile --args=--smoke`) uses ephemeral ports
and tests synthetic A/B personal channels while C retains the common NPC.

For actual client acceptance, connect A and B (optionally C), accept the pack:

1. A and B use `/personal`; only that connection changes to a private visual group.
2. A uses `/personalwave`; only A sees the gesture and hears the villager cue.
   B uses `/personallook`; only B sees their private head pose. C sees the common NPC.
3. `/npcwave` and `/npclook` change shared channels; a private channel stays private.
   `/personalfollow` releases both channels to the latest shared state.
4. `/personalidle`, `/personalstop`, `/npcscale` and `/npcmove` must not replay a cue.
5. Move outside visibility and return: current pose, no repeated private cue.
6. `/personalclose` restores the latest common model. Reconnect inherits no private
   state. `/npcremove` drains all common/private displays and stops shared ticking.

The demo has no dialogue/UI, quest state or cross-proxy transport. Synthetic smoke
does not replace simultaneous client visual/audio and real backend-transfer tests.
