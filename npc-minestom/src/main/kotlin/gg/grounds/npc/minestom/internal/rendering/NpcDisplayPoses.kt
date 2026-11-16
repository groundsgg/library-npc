package gg.grounds.npc.minestom.internal.rendering

import gg.grounds.npc.core.animation.NpcPoseBinding
import gg.grounds.npc.core.animation.NpcPoseSampler
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel

/** Preserve the original once-per-model client-mesh correction in the part bind cache. */
internal class NpcDisplayPoses(model: NpcModel) {
    private val binding = prepared(model)

    fun sample(clipId: String?, seconds: Double, headLook: NpcVector3): List<NpcMatrix> =
        binding.sample(clipId, seconds, headLook).parts

    companion object {
        private val correction = NpcMatrix.rotation(NpcVector3(0.0, 180.0, 0.0))
        private val bindings =
            java.util.WeakHashMap<NpcModel, java.lang.ref.WeakReference<NpcPoseBinding>>()

        private fun prepared(model: NpcModel): NpcPoseBinding =
            synchronized(bindings) {
                bindings[model]?.get()
                    ?: NpcPoseSampler.prepare(model, correction).also {
                        bindings[model] = java.lang.ref.WeakReference(it)
                    }
            }
    }
}
