# NPC API

`npc-core` has immutable numeric definitions, a bounded JSON decoder and a pure
pose sampler. It imports no Scene or Minecraft types. IDs are namespaced strings,
vectors use authored model units and rotations use XYZ degrees. Clip sampling uses
seconds; the renderer's externally driven clock uses elapsed milliseconds.
The decoder evaluates no scripts or authored expressions.
`NpcPoseSampler.prepare(model, partPostTransform)` caches an immutable generic part
binding. Minestom supplies its mesh-basis correction there; the core has no
Minecraft-specific coordinate choice. Read matrix elements with `get(index)` and
compare with `contentEquals` without allocating array snapshots.

`npc-minestom` owns ItemDisplay entities and viewer metadata. A host calls
`NpcRenderer.prepare(NpcSpawnRequest)` and retains the returned `NpcStart`
immediately. `ready()` yields `Ready(handle)` or `Rejected(problems)`. Preparation
attaches hidden parts; no viewer sees them until `handle.publish()` succeeds.
A host generation guard must atomically check validity and run the publication
operation. It is called outside the NPC ownership lock.

All handle commands return `CompletionStage<Void>` and are queued onto the owning
instance scheduler. Hosts observe errors; commands are not guarantees of synchronous
client rendering. The host advances clip time explicitly, without changing world
age or server time. `applyTransform` waits for destination loading and relocation.
The renderer keeps existing per-distance update cohorts and hysteresis.

`NpcStart.cancel()` and `NpcHandle.closeAsync()` are idempotent and work without
another scheduler tick. They reject queued commands, remove owned entities and
wait for pending attachments/movement to drain. Retain that stage in the host's
shutdown/unload drain. Cleanup failures complete it exceptionally. A ready result
may race host invalidation; always use a generation guard and retain start ownership.

`NpcMeshResolver` supplies item meshes, `NpcSoundSink` supplies sound delivery and
`NpcRenderConfig` supplies view distances, timing and explicit idle clips.
Default mesh resolution uses PAPER plus the namespaced item-model component.
The default sound sink emits nothing. The library discovers no product assets.

`applyViewerState` supports connection-specific scale and glow; state is attached
to the actual Player connection and instance, not a reusable account UUID.
It is not an independent per-player animation clock or private head pose.
`NpcAnchorSnapshot` exposes the immutable root and optional model-local head origin.
Hosts choose dialogue text anchors separately.

`NpcDisplayTransforms` exposes the shared affine/TRSR decomposition for placeholder
bounds or model/world matrices. Its boolean reports changes after float
serialization so unchanged fields do not restart interpolation. ItemDisplay mesh
facing correction lives only in the renderer; it is absent from core poses.
