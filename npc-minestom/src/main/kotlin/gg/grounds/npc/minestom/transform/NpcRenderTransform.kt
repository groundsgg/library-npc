package gg.grounds.npc.minestom.transform

import gg.grounds.npc.core.math.NpcTransform

/**
 * Immutable shared root/world transform and optional local model transform. Null local transform is
 * identity; the root position is the entity anchor.
 *
 * @property root World/root transform whose position anchors the entity group.
 * @property local Additional model-local transform, or null for identity.
 */
public data class NpcRenderTransform(val root: NpcTransform, val local: NpcTransform? = null)
