package gg.grounds.npc.core.model

import gg.grounds.npc.core.animation.NpcClip
import gg.grounds.npc.core.internal.NpcModelEvaluator
import gg.grounds.npc.core.internal.NpcModelValidator
import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.internal.immutableMap

/** Validated immutable numeric asset definition. Collections are copied; invalid hierarchy, IDs, clips or sounds throw IllegalArgumentException. */
class NpcModel(
    val id: String,
    val bounds: NpcBounds,
    bones: List<NpcBone>,
    parts: List<NpcPart>,
    animations: Map<String, NpcClip>,
    sounds: Map<String, NpcClipSound>,
) {
    val bones: List<NpcBone> = immutableList(bones)
    val parts: List<NpcPart> = immutableList(parts)
    val animations: Map<String, NpcClip> = immutableMap(animations)
    val sounds: Map<String, NpcClipSound> = immutableMap(sounds)

    init { NpcModelValidator.validate(this) }

    /** The unique bone named head, ignoring case; null if absent or ambiguous. */
    val headBone: NpcBone? = this.bones.singleOrNull { it.name.equals("head", ignoreCase = true) }
    internal val evaluation = NpcModelEvaluator(this)
}
