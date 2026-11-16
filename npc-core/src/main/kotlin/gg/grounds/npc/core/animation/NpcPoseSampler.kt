package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.pose
import gg.grounds.npc.core.internal.transformedParts
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel

object NpcPoseSampler {
    /** Model-local authored pose; Minecraft's mesh-facing correction belongs to the adapter. */
    @JvmStatic
    @JvmOverloads
    fun sample(
        model: NpcModel,
        clipId: String?,
        seconds: Double,
        headLook: NpcVector3? = null,
    ): NpcPose {
        validate(model, clipId, seconds)
        return NpcPose(model.pose(clipId, seconds, headLook))
    }

    /** Precompute a neutral right-multiplied part binding, e.g. a host mesh basis correction. */
    @JvmStatic
    fun prepare(model: NpcModel, partPostTransform: NpcMatrix): NpcPoseBinding =
        NpcPoseBinding(model, model.transformedParts(partPostTransform))

    internal fun validate(model: NpcModel, clipId: String?, seconds: Double) {
        require(seconds.isFinite() && seconds >= 0) { "Sample time must be finite and nonnegative" }
        require(clipId == null || clipId in model.animations) { "Unknown clip: $clipId" }
    }
}
