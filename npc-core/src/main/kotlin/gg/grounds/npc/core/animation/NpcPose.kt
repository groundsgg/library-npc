package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.math.NpcMatrix

/**
 * Immutable part matrices in authored model-part order. Matrix arrays remain defensive snapshots
 * and cannot mutate cached bind state.
 *
 * @property parts Immutable sampled matrices in authored model-part order.
 */
public class NpcPose(parts: Collection<NpcMatrix>) {
    /** Immutable sampled matrices in authored model-part order. */
    public val parts: List<NpcMatrix> = immutableList(parts)
}
