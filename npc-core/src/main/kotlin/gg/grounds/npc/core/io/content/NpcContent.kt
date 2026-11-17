package gg.grounds.npc.core.io.content

import com.google.gson.JsonObject
import gg.grounds.npc.core.internal.content.ContentPackVerifier
import gg.grounds.npc.core.model.NpcModel
import java.nio.file.Path
import java.util.Collections

/** A complete validated content snapshot; own it for one application/server run. */
public class NpcContent
internal constructor(
    /** Deterministic identity of models and their client-asset contract. */
    public val contentId: String,
    /** Explicit supported Minecraft target, currently 26.2 or 26.3. */
    public val target: String,
    models: Map<String, NpcModel>,
    contract: JsonObject,
) {
    /** Immutable model lookup by namespaced ID. */
    public val models: Map<String, NpcModel> = Collections.unmodifiableMap(LinkedHashMap(models))
    private val contract: JsonObject = contract.deepCopy()

    /**
     * Verify dedicated asset bytes, contributed sound definitions and effective pack metadata.
     * Foreign assets and disjoint sound entries are allowed. Does not send or activate the pack.
     *
     * @throws NpcContentException if the pack violates this content's contract.
     * @throws java.io.IOException if the pack cannot be read.
     */
    @Throws(java.io.IOException::class)
    public fun verifyResourcePack(path: Path) {
        ContentPackVerifier.verify(path, target, contract)
    }
}
