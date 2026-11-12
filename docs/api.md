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
Private clip/head channels use the separate additive presentation API below.
`NpcAnchorSnapshot` exposes the immutable root and optional model-local head origin.
Hosts choose dialogue text anchors separately.

`NpcDisplayTransforms` exposes the shared affine/TRSR decomposition for placeholder
bounds or model/world matrices. Its boolean reports changes after float
serialization so unchanged fields do not restart interpolation. ItemDisplay mesh
facing correction lives only in the renderer; it is absent from core poses.


## Personal presentations (N7 candidate)

Retain `NpcPresentations.prepare(parent, new NpcPresentationOptions(player))`
immediately. Its `NpcPresentationStart` owns cancellation while preparation is
pending. `ready()` yields `NpcPresentationResult.Ready` or `Rejected`; the Ready
handle remains hidden until its queued `publish()` succeeds. Hosts must observe
stages and retain `cancel()` in their shutdown drain. The common parent also owns
all pending/active copies and retains their cleanup failures.

Clip and head start in `FOLLOW_SHARED`, using the actual common sample (including
seek, pause and stop). `playClip` switches only the clip to `PRIVATE` and starts
local monotonic time at zero. `applyHeadLook` switches only the head. A one-shot
or `stopClip` enters the configured private idle, otherwise rest; it stays private.
`followSharedClip` and `followSharedHeadLook` release their channels explicitly.
The private task samples every 50 ms by default; no host advance calls are needed.
`snapshot()` is diagnostic; `closed()` observes automatic expiry or runtime errors.

The default personal sound sink is silent. Supply `NpcViewerSoundSink` to deliver
one-shot cues exclusively to its concrete Player, with no spatial fallback.
Only explicit private clip starts emit cues. Reentry, snapshots, common movement,
scale/glow and channel changes emit none. Existing shared sounds keep their
existing spatial behavior. Removing a presentation does not stop already delivered
client audio. Callback completion is drained without holding NPC locks.

Root/local transforms and that participant's scale/glow follow the common parent,
without resetting private channels. A successful parent move waits for still-owned
copies too. There is no private position/collider API. Only the owner sees the
private group; observers retain the common group. Close removes private parts
before restoring the current common pose, only for the still-valid connection and
parent generation. A superseded lease cannot release a newer lease.

Only one pending/active presentation per concrete parent/Player is admitted.
Default `NpcPresentationLimits` are 32 presentations and 1024 additional parts;
50-part models therefore admit 20 groups, and reject the 21st before hiding the
common NPC. Use the additive `NpcRenderer.prepare(request, limits)` overload to
raise limits deliberately. There is no full-player scan per private copy.

Distance/chunk reentry preserves the current private clock and sends no duplicate
cue. Disconnect and instance changes invalidate the handle; cleanup runs on its
owned task or an earlier explicit close. A new connection with the same UUID
inherits nothing. Never transfer live handles, Player references or Entity IDs
across servers/proxies.
