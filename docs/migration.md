# Migrate from NPC 0.2.0 to 1.0.0

NPC 1.0.0 intentionally breaks source and JVM signatures. Rebuild Java and Kotlin consumers together with the matching Scene adapter. Kotlin typealiases do not provide a Java binary migration.

| Previous API | Current source API |
| --- | --- |
| `gg.grounds.npc.core` model types | `gg.grounds.npc.core.model` |
| Core vector/matrix/transform | `gg.grounds.npc.core.math` |
| Core clips/keyframes/pose sampler | `gg.grounds.npc.core.animation` |
| Core JSON/result/limits | `gg.grounds.npc.core.io` |
| `NpcProblem` | `gg.grounds.npc.core.validation.NpcProblem` |
| Minestom configuration, transforms, viewer state and callbacks at root | `config`, `transform`, `viewer` and `spi` packages |
| Personal APIs at root | `gg.grounds.npc.minestom.presentation` |
| Optional positional options constructors and `copy` | `builder(...).property(value).build()` and `toBuilder()` |
| `startAnimation(id, millis)` | `playClip(id, millis)` or `playClip(id)` at zero |
| `advanceAnimation(millis)` | `advanceClipTo(millis)`, still absolute milliseconds |
| `stopAnimation(null)` | `stopClip()` |
| `stopAnimation(id)` | `stopClipIfPlaying(id)` |
| `NpcPresentations.prepare(parent, options)` | `parent.preparePresentation(options)` |
| Clip loop/interpolation strings | `NpcLoopMode` and `NpcInterpolation` |
| Nested track string maps | Bone-ID map of `NpcBoneTrack(position, rotation, scale)` |
| Clip/keyframe `.length` / `.time` | `.lengthSeconds` / `.timeSeconds` |
| Package constants/operators and public `sampleNpcTrack` | `NpcVector3.ZERO/ONE`, member operators and internal track sampling |

Rejected preparation results also copy their diagnostics and require at least one problem; they no longer expose data-class `copy` or destructuring.

Regular immutable option classes replace extensible `data class` configuration. Fixed mathematical value types retain their value semantics. Public `NpcHandle.closed()` observes final cleanup without closing. Argument errors stay in command stages; operation errors preserve their cause. Delegating handles can forward personal preparation directly.

Schema-1 JSON, resource IDs, authored units, interpolation curves, sounds and host asset catalogue checks remain unchanged. Existing models/resource packs do not need a schema conversion. Runtime interval limitations recorded in compatibility still apply.

## Coordinated Grounds migration

Use the published NPC 1.0.0 packages, rebuild the matching Scene NPC adapter against them, then build Lobby and Dialogue against that same released dependency set. Scene exports NPC in its API, so updating an NPC JAR without rebuilding Scene/consumers is insufficient. Check dependency reports and loaded JAR hashes. Independent consumers use Maven publications, not source substitution.

Scene still owns generations, activation, colliders and the host resource drain. NPC owns render entities. Keep the original host generation guard and await cancellation even when preparation never returns a handle. Invalidated/failed installation must drain rollback before reporting completion. Lobby retains exact catalogue/bounds/clip/sound and pack-digest validation; private licensed resources remain outside public artifacts.

Dialogue’s base Minestom module has no NPC dependency. Its optional NPC consumer migrates its pin, Java model, personal actor adapter and tests. Retain separate prepare/publish/cancel, private sound isolation, cancellation while preparing and shared-NPC restoration. Do not replace the independently accepted Dialogue rendering/navigation changes.

Real visual/audio, licensed-content and cross-backend/proxy acceptance remain separate from compilation and synthetic smoke. A new connection/backend must establish new ownership; no live handle, Player reference, entity ID or viewer override is transferable. Keep the previous complete candidate set for rollback rather than mixing old and new JARs.
