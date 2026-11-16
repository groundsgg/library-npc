package gg.grounds.npc.minestom.internal.rendering

import gg.grounds.npc.minestom.transform.NpcRenderTransform

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcClipSound
import gg.grounds.npc.core.model.NpcModel
import gg.grounds.npc.core.validation.NpcProblem
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.abs
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/** Model pose evaluation and float-visible display metadata projection with reusable caches. */
internal class NpcDisplayRenderer(model: NpcModel, private val displays: List<NpcItemDisplay>) {
    private val poses = NpcDisplayPoses(model)
    private var previousPose: List<NpcMatrix>? = null
    private var previousTransform: NpcRenderTransform? = null
    fun sample(name: String?, seconds: Double, headLook: NpcVector3): List<NpcMatrix> = poses.sample(name, seconds, headLook)
    fun apply(pose: List<NpcMatrix>, transform: NpcRenderTransform, renderInterval: Long, deferUpdates: Boolean) {
        val unchangedRoot = previousTransform == transform
        val previous = previousPose
        val duration = (renderInterval / 50).toInt()
        displays.forEachIndexed { index, display ->
            if (unchangedRoot && previous != null && previous[index].contentEquals(pose[index]))
                return@forEachIndexed
            // Keep entry preparation queued until existing viewers' deadlines.
            display.deferUpdates = deferUpdates
            try {
                display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    if (
                        DisplayTransform.fromModel(transform, NPC_FACING * pose[index])
                            .apply(meta, transform.root.position)
                    ) {
                        meta.setTransformationInterpolationDuration(duration)
                        meta.setTransformationInterpolationStartDelta(0)
                    }
                }
            } finally {
                display.deferUpdates = false
            }
        }
        previousPose = pose
        previousTransform = transform
    }
}
private val NPC_FACING = NpcMatrix.rotation(NpcVector3(0.0, 180.0, 0.0))

