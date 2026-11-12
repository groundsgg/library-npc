package gg.grounds.npc.minestom

import gg.grounds.npc.core.*
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcDisplayTransformsTest {
    @Test
    fun `public shared transform applies bounds and avoids subfloat interpolation restarts`() {
        MinecraftServer.init()
        val meta = Entity(EntityType.ITEM_DISPLAY).entityMeta as ItemDisplayMeta
        val transform = NpcRenderTransform(NpcTransform(NpcVector3(10.0, 20.0, 30.0)))
        val bounds = NpcBounds(NpcVector3(1.0, 2.0, 3.0), NpcVector3(2.0, 4.0, 6.0))
        assertTrue(
            NpcDisplayTransforms.applyBounds(meta, transform, bounds, NpcVector3(10.0, 20.0, 30.0))
        )
        assertEquals(0.0, meta.translation.x())
        assertEquals(0.0, meta.translation.y())
        assertEquals(0.0, meta.translation.z())
        assertEquals(4.0, meta.scale.y())
        val identity = NpcRenderTransform(NpcTransform(NpcVector3.ZERO))
        assertTrue(
            NpcDisplayTransforms.applyModel(
                meta,
                identity,
                NpcMatrix.translation(NpcVector3(1.0, 2.0, 3.0)),
            )
        )
        assertFalse(
            NpcDisplayTransforms.applyModel(
                meta,
                identity,
                NpcMatrix.translation(NpcVector3(1.0 + 1e-10, 2.0, 3.0)),
            )
        )
    }
}
