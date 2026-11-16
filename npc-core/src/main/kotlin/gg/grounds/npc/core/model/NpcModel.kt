package gg.grounds.npc.core.model

import gg.grounds.npc.core.animation.NpcClip
import gg.grounds.npc.core.internal.NpcModelEvaluator
import gg.grounds.npc.core.internal.NpcModelValidator
import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.internal.immutableMap
import gg.grounds.npc.core.io.NpcDecodeLimits

/**
 * Validated immutable numeric NPC definition. Inputs are defensively copied; invalid IDs,
 * hierarchy, clips or sounds throw IllegalArgumentException. No scripts, catalogues or platform
 * resources are evaluated or discovered.
 *
 * @property id Namespaced asset identity, at most 256 characters.
 * @property bounds Authored model-local bounds.
 * @property bones Immutable bones in parent-before-child order, at most 256.
 * @property parts Immutable model parts or sampled matrices in authored order, at most 1024 model
 *   parts.
 * @property animations Immutable clip-ID mapping, at most 128 clips.
 * @property sounds Immutable cues keyed by existing clip IDs.
 * @property headBone The unique case-insensitive head bone, or null when absent/ambiguous.
 */
public class NpcModel
internal constructor(
    /** Namespaced asset identity, at most 256 characters. */
    public val id: String,
    /** Authored model-local bounds. */
    public val bounds: NpcBounds,
    bones: List<NpcBone>,
    parts: List<NpcPart>,
    animations: Map<String, NpcClip>,
    sounds: Map<String, NpcClipSound>,
    limits: NpcDecodeLimits,
) {
    /** Construct an immutable definition validated against the hard schema limits. */
    public constructor(
        id: String,
        bounds: NpcBounds,
        bones: List<NpcBone>,
        parts: List<NpcPart>,
        animations: Map<String, NpcClip>,
        sounds: Map<String, NpcClipSound>,
    ) : this(id, bounds, bones, parts, animations, sounds, NpcDecodeLimits())

    /** Immutable bones in parent-before-child order, at most 256. */
    public val bones: List<NpcBone> = immutableList(bones)
    /** Immutable model parts or sampled matrices in authored order, at most 1024 model parts. */
    public val parts: List<NpcPart> = immutableList(parts)
    /** Immutable clip-ID mapping, at most 128 clips. */
    public val animations: Map<String, NpcClip> = immutableMap(animations)
    /** Immutable cues keyed by existing clip IDs. */
    public val sounds: Map<String, NpcClipSound> = immutableMap(sounds)

    init {
        NpcModelValidator.validate(this, limits)
    }

    /** The unique bone named head, ignoring case; null if absent or ambiguous. */
    public val headBone: NpcBone? =
        this.bones.singleOrNull { it.name.equals("head", ignoreCase = true) }
    internal val evaluation = NpcModelEvaluator(this)
}
