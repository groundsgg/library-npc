package gg.grounds.npc.core.model

import gg.grounds.npc.core.math.NpcVector3

/**
 * A numeric mesh-part binding. Centres/pivots are model-local authored coordinates; rotations use
 * XYZ degrees. A null bone binds directly to the model root.
 *
 * @property bone Bound bone ID, or null to use the model root.
 * @property model Namespaced item-model resource ID resolved by the host mesh resolver.
 * @property center Centre in authored model coordinates.
 * @property pivot Authored rotation/scale pivot in model-local units.
 * @property rotation XYZ rotation in finite degrees.
 * @property scale Component scale; negative components reflect the geometry.
 */
public data class NpcPart(
    /** Bound bone ID, or null to use the model root. */
    val bone: String?,
    /** Namespaced item-model resource ID resolved by the host mesh resolver. */
    val model: String,
    /** Centre in authored model coordinates. */
    val center: NpcVector3,
    /** Authored rotation/scale pivot in model-local units. */
    val pivot: NpcVector3,
    /** XYZ rotation in finite degrees. */
    val rotation: NpcVector3,
    /** Component scale; negative components reflect the geometry. */
    val scale: NpcVector3 = NpcVector3(1.0, 1.0, 1.0),
)
