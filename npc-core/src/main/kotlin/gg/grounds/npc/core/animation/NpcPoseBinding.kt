package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.internal.samplePose
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel

/**
 * Immutable cached part binding for repeated sampling. The caller chooses the post-transform
 * coordinate correction; sampling never mutates the model.
 */
public class NpcPoseBinding
internal constructor(private val model: NpcModel, parts: List<NpcMatrix>) {
    private val parts = immutableList(parts)

    /**
     * Sample the prepared binding at a finite nonnegative time in seconds. Null clip samples rest;
     * unknown IDs or invalid time throw IllegalArgumentException. Head look is additive XYZ degrees
     * and affects only the named subtree.
     */
    @JvmOverloads
    public fun sample(clipId: String?, seconds: Double, headLook: NpcVector3? = null): NpcPose {
        NpcPoseSampler.validate(model, clipId, seconds)
        return NpcPose(model.samplePose(clipId, seconds, headLook, parts))
    }
}
