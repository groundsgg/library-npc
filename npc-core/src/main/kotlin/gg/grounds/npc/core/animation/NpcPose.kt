package gg.grounds.npc.core.animation

import gg.grounds.npc.core.internal.immutableList
import gg.grounds.npc.core.math.NpcMatrix

/** Immutable sampled part matrices in authored part order; matrix arrays are defensive snapshots. */
class NpcPose(parts: Collection<NpcMatrix>) {
    val parts: List<NpcMatrix> = immutableList(parts)
}
