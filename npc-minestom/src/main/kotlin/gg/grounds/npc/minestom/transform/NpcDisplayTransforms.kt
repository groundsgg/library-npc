package gg.grounds.npc.minestom.transform

import gg.grounds.npc.core.math.NpcMatrix
import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3
import gg.grounds.npc.core.model.NpcBounds
import gg.grounds.npc.minestom.internal.rendering.DisplayTransform
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta

/** Shared affine-to-display decomposition. Returns whether client-visible fields changed. */
public object NpcDisplayTransforms {
    /**
     * Project a world affine matrix into display TRSR relative to anchor. Call on the owning
     * scheduler; true means float-visible fields changed. Does not restart interpolation by itself.
     */
    @JvmStatic
    @JvmOverloads
    public fun apply(
        meta: AbstractDisplayMeta,
        worldMatrix: NpcMatrix,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean =
        applyModel(meta, NpcRenderTransform(NpcTransform(NpcVector3.ZERO)), worldMatrix, anchor)

    /**
     * Project authored bounds through root/local transforms into display TRSR relative to anchor.
     * Call on the owning scheduler; true means client-visible fields changed.
     */
    @JvmStatic
    @JvmOverloads
    public fun applyBounds(
        meta: AbstractDisplayMeta,
        transform: NpcRenderTransform,
        bounds: NpcBounds,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean = DisplayTransform.from(transform, bounds).apply(meta, anchor)

    /**
     * Project an authored model matrix through root/local transforms into display TRSR relative to
     * anchor. Does not apply the renderer’s item-facing correction; true means serialized fields
     * changed.
     */
    @JvmStatic
    @JvmOverloads
    public fun applyModel(
        meta: AbstractDisplayMeta,
        transform: NpcRenderTransform,
        model: NpcMatrix,
        anchor: NpcVector3 = NpcVector3.ZERO,
    ): Boolean = DisplayTransform.fromModel(transform, model).apply(meta, anchor)
}
