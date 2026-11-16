package gg.grounds.npc.core.math

/** Position, XYZ rotation in degrees and component scale; defaults preserve rotation and size. */
data class NpcTransform
@JvmOverloads
constructor(
    val position: NpcVector3,
    val rotationDegrees: NpcVector3 = NpcVector3.ZERO,
    val scale: NpcVector3 = NpcVector3.ONE,
)
