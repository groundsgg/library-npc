package gg.grounds.npc.importer.model

import gg.grounds.npc.core.model.NpcModel
import java.util.Collections

/** Immutable validated single-model import, including its generated dedicated client assets. */
public class NpcImportedModel
internal constructor(
    /** Numeric runtime definition, independent of any server platform. */
    public val model: NpcModel,
    modelJson: ByteArray,
    resources: Map<String, ByteArray>,
) {
    private val json = modelJson.copyOf()
    private val files = resources.mapValues { it.value.copyOf() }
    /** Defensive schema-1 UTF-8 model bytes, ready for an application-owned content source. */
    public val modelJson: ByteArray
        get() = json.copyOf()

    /** Defensive immutable lookup of generated pack-relative file bytes. */
    public val resources: Map<String, ByteArray>
        get() = Collections.unmodifiableMap(files.mapValues { it.value.copyOf() })
}
