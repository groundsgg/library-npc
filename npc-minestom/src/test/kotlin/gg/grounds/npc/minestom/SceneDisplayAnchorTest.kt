package gg.grounds.npc.minestom

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SceneDisplayAnchorTest {
    @Test
    fun `scene display anchors stay authored through real entity physics ticks in empty air`() {
        MinecraftServer.init()
        RendererFixture().use { fixture ->
            val anchor = Pos(0.0, 173.0, 0.0)
            for (display in listOf(NpcItemDisplay(Any()))) {
                display.setInstance(fixture.instance, anchor).join()
                repeat(20) { display.tick(it * 50L) }
                assertEquals(
                    anchor.y(),
                    display.position.y(),
                    1e-8,
                    "Display fell under gravity: ${display.entityType}",
                )
                display.remove()
            }
        }
    }
}
