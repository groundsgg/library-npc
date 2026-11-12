package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcBounds
import gg.grounds.npc.core.NpcMatrix
import gg.grounds.npc.core.NpcTransform
import gg.grounds.npc.core.NpcVector3
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta

/** Shared affine-to-display decomposition. Returns whether client-visible fields changed. */
object NpcDisplayTransforms {
    @JvmStatic
    @JvmOverloads
    fun apply(
        meta: AbstractDisplayMeta,
        worldMatrix: NpcMatrix,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean =
        applyModel(meta, NpcRenderTransform(NpcTransform(NpcVector3.ZERO)), worldMatrix, anchor)

    @JvmStatic
    @JvmOverloads
    fun applyBounds(
        meta: AbstractDisplayMeta,
        transform: NpcRenderTransform,
        bounds: NpcBounds,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean = DisplayTransform.from(transform, bounds).apply(meta, anchor)

    @JvmStatic
    @JvmOverloads
    fun applyModel(
        meta: AbstractDisplayMeta,
        transform: NpcRenderTransform,
        model: NpcMatrix,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean = DisplayTransform.fromModel(transform, model).apply(meta, anchor)
}
