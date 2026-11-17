package gg.grounds.npc.core.internal

import gg.grounds.npc.core.animation.NpcLoopMode
import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcModel

internal class NpcModelEvaluator(private val model: NpcModel) {
    private val bones = model.bones
    private val parts = model.parts
    private val animations = model.animations
    private val boneIndices = bones.mapIndexed { index, bone -> bone.id to index }.toMap()
    private val origins = bones.associate { it.id to it.origin }
    private val parents = IntArray(bones.size) { bones[it].parent?.let(boneIndices::get) ?: -1 }
    private val headIndex = model.headBone?.id?.let(boneIndices::get) ?: -1
    private val bindBones = bones.map { bone ->
        NpcMatrix.translation(bone.origin - (bone.parent?.let(origins::get) ?: NpcVector3.ZERO)) *
            NpcMatrix.rotation(bone.rotation)
    }
    private val partBones = IntArray(parts.size) { parts[it].bone?.let(boneIndices::get) ?: -1 }
    private val bindParts = parts.map { part ->
        NpcMatrix.translation(part.pivot - (part.bone?.let(origins::get) ?: NpcVector3.ZERO)) *
            NpcMatrix.rotation(part.rotation) *
            NpcMatrix.translation(part.center - part.pivot) *
            NpcMatrix.scale(part.scale)
    }

    internal fun transformedParts(postTransform: NpcMatrix): List<NpcMatrix> = bindParts.map {
        it * postTransform
    }

    @JvmOverloads
    internal fun pose(
        animation: String?,
        seconds: Double,
        headLook: NpcVector3? = null,
    ): List<NpcMatrix> = samplePose(animation, seconds, headLook, bindParts)

    internal fun samplePose(
        animation: String?,
        seconds: Double,
        headLook: NpcVector3?,
        localParts: List<NpcMatrix>,
    ): List<NpcMatrix> {
        val clip = animation?.let(animations::get)
        val time = if (clip?.loop == NpcLoopMode.LOOP) seconds % clip.lengthSeconds else seconds
        val world = arrayOfNulls<NpcMatrix>(bones.size)
        val look = headLook?.takeUnless { it == NpcVector3.ZERO }?.let(NpcMatrix::rotation)
        for ((index, bone) in bones.withIndex()) {
            val channels = clip?.tracks?.get(bone.id)
            if (
                channels == null ||
                    (channels.position.isEmpty() &&
                        channels.rotation.isEmpty() &&
                        channels.scale.isEmpty())
            ) {
                val local =
                    if (index == headIndex && look != null) bindBones[index] * look
                    else bindBones[index]
                world[index] = (world.getOrNull(parents[index]) ?: NpcMatrix.IDENTITY) * local
                continue
            }
            val position =
                sampleNpcTrack(
                    channels.position,
                    time,
                    NpcVector3.ZERO,
                    clip?.loop == NpcLoopMode.LOOP,
                )
            val rotation =
                bone.rotation +
                    sampleNpcTrack(
                        channels.rotation,
                        time,
                        NpcVector3.ZERO,
                        clip?.loop == NpcLoopMode.LOOP,
                    )
            val scale =
                sampleNpcTrack(channels.scale, time, NpcVector3.ONE, clip?.loop == NpcLoopMode.LOOP)
            val local =
                NpcMatrix.translation(
                    bone.origin - (bone.parent?.let(origins::get) ?: NpcVector3.ZERO) + position
                ) *
                    NpcMatrix.rotation(rotation) *
                    (if (index == headIndex && look != null) look else NpcMatrix.IDENTITY) *
                    NpcMatrix.scale(scale)
            world[index] = (world.getOrNull(parents[index]) ?: NpcMatrix.IDENTITY) * local
        }
        return localParts.mapIndexed { index, local ->
            // Sampled matrices are immutable; callers receive defensive array snapshots through
            // values.
            (world.getOrNull(partBones[index]) ?: NpcMatrix.IDENTITY) * local
        }
    }
}

internal fun NpcModel.pose(
    animation: String?,
    seconds: Double,
    headLook: NpcVector3? = null,
): List<NpcMatrix> = evaluation.pose(animation, seconds, headLook)

internal fun NpcModel.transformedParts(postTransform: NpcMatrix): List<NpcMatrix> =
    evaluation.transformedParts(postTransform)

internal fun NpcModel.samplePose(
    animation: String?,
    seconds: Double,
    headLook: NpcVector3?,
    parts: List<NpcMatrix>,
): List<NpcMatrix> = evaluation.samplePose(animation, seconds, headLook, parts)
