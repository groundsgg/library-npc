package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.samplePose

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel

/** Immutable prepared binding. Coordinate-system choices remain entirely with the caller. */
class NpcPoseBinding internal constructor(private val model: NpcModel, parts: List<NpcMatrix>) {
    private val parts = immutableList(parts)

    @JvmOverloads
    fun sample(clipId: String?, seconds: Double, headLook: NpcVector3? = null): NpcPose {
        NpcPoseSampler.validate(model, clipId, seconds)
        return NpcPose(model.samplePose(clipId, seconds, headLook, parts))
    }
}
