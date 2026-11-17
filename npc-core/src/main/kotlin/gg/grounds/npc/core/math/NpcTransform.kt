package gg.grounds.npc.core.math

/**
 * Immutable translation, XYZ rotation in degrees and component scale. Identity rotation/scale are
 * the defaults; negative scale supports reflection.
 *
 * @property position Translation vector in the owning coordinate space.
 * @property rotationDegrees XYZ rotation in finite degrees.
 * @property scale Component scale; negative components reflect the geometry.
 */
public data class NpcTransform
@JvmOverloads
constructor(
    /** Translation vector in the owning coordinate space. */
    val position: NpcVector3,
    /** XYZ rotation in finite degrees. */
    val rotationDegrees: NpcVector3 = NpcVector3.ZERO,
    /** Component scale; negative components reflect the geometry. */
    val scale: NpcVector3 = NpcVector3.ONE,
)
