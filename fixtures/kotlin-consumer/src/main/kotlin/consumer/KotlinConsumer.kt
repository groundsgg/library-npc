package consumer

import gg.grounds.npc.core.*

fun main() {
    val model =
        NpcModel(
            "example:kotlin",
            NpcBounds(NpcVector3.ZERO, NpcVector3.ONE),
            emptyList(),
            listOf(
                NpcPart(null, "example:cube", NpcVector3.ZERO, NpcVector3.ZERO, NpcVector3.ZERO)
            ),
            emptyMap(),
            emptyMap(),
        )
    check(NpcPoseSampler.sample(model, null, 0.0).parts.size == 1)
    println("Kotlin consumer OK: ${model.id}")
}
