package gg.grounds.npc.minestom.transform

import gg.grounds.npc.core.math.NpcTransform
import gg.grounds.npc.core.math.NpcVector3

/**
 * Immutable diagnostic root and optional model-local head origin. Hosts choose text/dialogue
 * anchors separately; sampling this value does not advance time.
 *
 * @property root World/root transform whose position anchors the entity group.
 * @property lookOrigin Model-local head origin, or null if no unique head exists.
 */
public data class NpcAnchorSnapshot(val root: NpcTransform, val lookOrigin: NpcVector3?)
