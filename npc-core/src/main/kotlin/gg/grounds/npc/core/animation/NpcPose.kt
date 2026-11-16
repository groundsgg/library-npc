package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.math.NpcMatrix

/**
 * Immutable part matrices in authored model-part order. Matrix arrays remain defensive snapshots
 * and cannot mutate cached bind state.
 *
 * @property parts Immutable model parts or sampled matrices in authored order, at most 1024 model
 *   parts.
 */
public class NpcPose(parts: Collection<NpcMatrix>) {
    /** Immutable model parts or sampled matrices in authored order, at most 1024 model parts. */
    public val parts: List<NpcMatrix> = immutableList(parts)
}
