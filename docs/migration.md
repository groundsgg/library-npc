# Migration boundaries

The old product runtime combines model definitions, numeric evaluation and an
implicit catalogue loader. The new NPC library keeps definitions/evaluation and
rendering. Grounds retains schema-1 content loading and its exact catalogue,
bounds, clips, sounds and pack-digest validation in a host adapter.

Product model JSON/index/digests move into `resourcepacks-npc-content`. Public
neutral artifacts have an empty index; purchased payload stays in private
server/client distributions. The engine does not include it. Existing resource
paths and map definitions remain compatible; there is no world-file migration.

Scene integration lives in the optional `scene-npc-minestom` module. Scene
owns element generations, activation, colliders and its resource drain. NPC owns
render entities. Hosts must invalidate the generation and await asynchronous NPC
cleanup before reporting unload/close success. Legacy Scene factories remain
supported. `SceneRendererLifetime.trackCleanup` owns a pending renderer start even
when it never delivers a handle; asynchronous cleanup errors also survive until
shutdown when reported earlier. Failed installation waits for its rollback drain. Placeholder displays share the extracted TRSR utility through a thin
transform/bounds adapter rather than retaining a second decomposition engine.

Lobby migration is accepted only after its original behaviour/performance suites,
Scene drain checks and a real Grounds 26.3 client check. Keep the previous complete
distribution for rollback, including matching content/catalogue/client-pack pins.
Never transfer live entity IDs or viewer overrides to another connection/backend.

N7 adds independent private clip/head/sound presentations through additive APIs.
The optional Scene adapter exposes `PersonalNpcRenderedAssetHandle`, obtained from
the concrete generation-bound factory handle. Existing Scene SPI methods remain
unchanged. The common NPC owns private cleanup, so the existing Scene drain also
waits for private preparation and cleanup failures. The new Scene branch requires
the explicit local N7 candidate until an actual NPC release is published; it is
not a released product dependency yet. Dialogue actor lookup remains future work.
