# NPC API contracts

The API is grouped by concepts. Core packages cover `model`, `animation`, `math`, `io` and `validation`; Minestom exposes lifecycle entrypoints at its root and focused `config`, `transform`, `viewer`, `spi`, `presentation` and `error` packages. Every supported entrypoint has KDoc. Generated reference: `./gradlew dokkaGenerate` → `build/dokka/html`.

## Models and sampling

`NpcModelJson.decode(bytes, limits)` returns a complete `Success` or immutable `Failure.problems`; malformed fields have a stable code and JSON path. Byte overflow and invalid UTF-8 have distinct codes. Manual model construction uses the same semantic validator and throws `IllegalArgumentException` for invalid definitions.

Bone parents precede children. Limits are 256 bones, 1024 parts, 128 clips, 4096 keys per channel and 300 seconds per clip. Decode limits may be stricter, never larger. IDs are authored namespaced strings, coordinates use authored model units, and rotation channels use XYZ degrees. Parsing evaluates no expressions or scripts.

`NpcClip` maps bone IDs to `NpcBoneTrack(position, rotation, scale)`. Each channel is an immutable ordered key list. `NpcLoopMode` and `NpcInterpolation` replace string switches while JSON keeps the original values. `NpcPoseSampler.sample` accepts finite nonnegative seconds; null clip means rest, and an unknown clip is an input error. `prepare` caches a generic post-transform binding. The Minestom-facing item correction stays in the adapter.

## Shared NPC lifecycle

1. Build immutable `NpcSpawnRequest` with its owning instance, validated model and transform. Configure named options through its builder; `toBuilder` copies without changing the original.
2. Call `NpcRenderer.prepare` and retain the returned `NpcStart` immediately.
3. Observe `start.ready()` as `Ready(handle)` or `Rejected(problems)`. Parts remain hidden.
4. Observe `handle.publish()`. A host generation guard must check validity and execute publication atomically and synchronously.
5. At unload/shutdown, invalidate host generation and await `start.cancel()` or `handle.closeAsync()`. Retain that stage in the host drain even when no handle became ready.

Command methods queue work onto the owning instance scheduler. Their returned `CompletionStage<Void>` observes server work, not completed client rendering. Argument failures are delivered through the command stage. A closed/unready runtime uses `NpcOperationException`; operational failures retain their cause. Configuration factories and manual numeric constructors validate synchronously.

`playClip(id)` starts at zero; `playClip(id, elapsedMillis)` chooses explicit clip-local time. The host calls `advanceClipTo(elapsedMillis)` with absolute nonnegative milliseconds, not frame deltas. Seeking backwards is supported. `stopClip()` stops the requested clip; `stopClipIfPlaying(id)` only stops a matching request. Shared clocks are not installed automatically and never change world age.

`applyTransform` waits for destination chunk loading, relocation and still-owned private copies. Head yaw/pitch use finite degrees and affect only the head subtree. `applyViewerState` applies scale/glow to one concrete online connection in this instance. Scale is finite and in (0, 16].

`closed()` observes final cleanup without causing it. Close is idempotent, rejects queued commands, cancels owner tasks and drains accepted I/O before owned displays are removed. It does not require another instance tick. Cleanup failures remain exceptional stage completions. Callbacks/completion handlers are not invoked while ownership monitors are held.

## Personal presentations

`parent.preparePresentation(NpcPresentationOptions.builder(player).build())` returns a retained cancellation owner before asynchronous readiness. `ready()` produces hidden Ready or Rejected. Publication hides the common group only for that concrete connection. The shared parent owns all pending/active groups and their cleanup.

Clip and head channels initially `FOLLOW_SHARED`, sampling the actual common pose including seeks/stops. Personal `playClip(id)` changes only the clip channel to `PRIVATE`, starts monotonic local time at zero and emits its explicit cue once. `applyHeadLook` changes only the head channel. `stopClip` enters configured private idle/rest and stays private. `followSharedClip` and `followSharedHeadLook` release channels explicitly.

The default private interval is 50 ms; no host advance calls are needed. Default sound sinks are silent. Supply `NpcViewerSoundSink` for sound exclusively to this connection, never spatial fallback. Reentry, snapshots, movement, scale/glow and follow/stop operations do not replay sound. Already delivered client audio is not stopped by removing a presentation.

Root/local transforms and per-viewer scale/glow follow the shared parent without resetting private channels. There is no private position/collider API. Close removes private displays before restoring the current common pose for the still-valid parent/connection. Reservations use leases so an older cleanup cannot release a newer group.

Default limits are 32 presentations and 1024 additional parts per parent. A 50-part model therefore admits 20 private groups. Admission checks happen before hiding the common NPC. Each personal group enumerates its own concrete connection, not all players.

Reconnect with the same UUID, departure from the instance and backend/proxy transfer invalidate ownership. Never serialize or transport live handles, Player references or entity IDs. Resolve the NPC and prepare new ownership at the destination. A custom/delegating `NpcHandle` now implements/forwards `preparePresentation` directly; there is no hidden internal-capability cast.

## Extension points

Mesh resolution is synchronous during preparation; an exception rejects preparation and drains allocated parts. Default meshes are PAPER with the namespaced item-model component. Host sound callbacks run outside NPC monitors; their completion is included in relevant command/cleanup ownership. Generation guards execute their operation synchronously at most once and return true only when they did so.

`NpcDisplayTransforms` exposes affine/TRSR projection for host placeholders and model matrices. Call on the owning scheduler. A true result means float-serialized fields changed; callers can restart interpolation once. This utility does not add the item-mesh facing correction.
