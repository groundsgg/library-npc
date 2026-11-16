package gg.grounds.npc.minestom

import gg.grounds.npc.core.validation.NpcProblem
import gg.grounds.npc.minestom.presentation.NpcPresentationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NpcRejectionContractTest {
    @Test
    fun `rejection diagnostics are nonempty immutable snapshots`() {
        val source = mutableListOf(NpcProblem("TEST", "$", "Rejected"))
        val preparation = NpcPrepareResult.Rejected(source)
        val presentation = NpcPresentationResult.Rejected(source)
        source.clear()
        for (problems in listOf(preparation.problems, presentation.problems)) {
            assertEquals(1, problems.size)
            assertThrows(UnsupportedOperationException::class.java) {
                (problems as MutableList).clear()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcPrepareResult.Rejected(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcPresentationResult.Rejected(emptyList())
        }
    }
}
