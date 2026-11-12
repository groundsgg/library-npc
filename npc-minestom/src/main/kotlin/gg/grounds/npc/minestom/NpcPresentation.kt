package gg.grounds.npc.minestom

import gg.grounds.npc.core.NpcClipSound
import gg.grounds.npc.core.NpcProblem
import gg.grounds.npc.core.NpcVector3
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.minestom.server.entity.Player

fun interface NpcMonotonicClock {
    fun nanoTime(): Long
}

fun interface NpcViewerSoundSink {
    fun playFor(player: Player, origin: NpcVector3, sound: NpcClipSound)
}

data class NpcPresentationLimits
@JvmOverloads
constructor(
    val maxPresentations: Int = 32,
    val maxAdditionalParts: Int = 1024,
) {
    init {
        require(maxPresentations > 0 && maxAdditionalParts > 0)
    }
}

data class NpcPresentationOptions
@JvmOverloads
constructor(
    val player: Player,
    val publicationGuard: NpcPublicationGuard = NpcPublicationGuard {
        it.run()
        true
    },
    val sounds: NpcViewerSoundSink = NpcViewerSoundSink { _, _, _ -> },
    val idleClip: String? = null,
    val sampleIntervalMillis: Long = 50,
    val clock: NpcMonotonicClock = NpcMonotonicClock(System::nanoTime),
) {
    init {
        require(idleClip == null || idleClip.isNotBlank())
        require(sampleIntervalMillis in 50..10_000 && sampleIntervalMillis % 50 == 0L)
    }
}

enum class NpcPresentationPhase {
    PREPARING,
    READY_HIDDEN,
    ACTIVE,
    CLOSING,
    CLOSED,
}

enum class NpcPresentationChannel {
    FOLLOW_SHARED,
    PRIVATE,
}

data class NpcPresentationSnapshot(
    val anchor: NpcAnchorSnapshot,
    val phase: NpcPresentationPhase,
    val clipMode: NpcPresentationChannel,
    val headMode: NpcPresentationChannel,
    val clipId: String?,
    val clipSeconds: Double,
)

interface NpcPresentationStart {
    fun ready(): CompletionStage<NpcPresentationResult>

    fun cancel(): CompletionStage<Void>
}

sealed interface NpcPresentationResult {
    data class Ready(val handle: NpcPresentationHandle) : NpcPresentationResult

    data class Rejected(val problems: List<NpcProblem>) : NpcPresentationResult
}

interface NpcPresentationHandle {
    fun publish(): CompletionStage<Void>

    fun playClip(clipId: String): CompletionStage<Void>

    fun stopClip(): CompletionStage<Void>

    fun followSharedClip(): CompletionStage<Void>

    fun applyHeadLook(yaw: Double, pitch: Double): CompletionStage<Void>

    fun followSharedHeadLook(): CompletionStage<Void>

    fun snapshot(): NpcPresentationSnapshot

    fun closeAsync(): CompletionStage<Void>
}

object NpcPresentations {
    @JvmStatic
    fun prepare(parent: NpcHandle, options: NpcPresentationOptions): NpcPresentationStart =
        (parent as? NpcPresentationParent)?.preparePresentation(options)
            ?: rejectedPresentation(
                "NPC_PRESENTATION_UNSUPPORTED",
                "Parent does not support personal presentations",
            )
}

internal interface NpcPresentationParent {
    fun preparePresentation(options: NpcPresentationOptions): NpcPresentationStart
}

internal fun rejectedPresentation(code: String, message: String): NpcPresentationStart =
    object : NpcPresentationStart {
        private val result =
            CompletableFuture.completedFuture<NpcPresentationResult>(
                NpcPresentationResult.Rejected(listOf(NpcProblem(code, "$", message)))
            )

        override fun ready(): CompletionStage<NpcPresentationResult> =
            result.minimalCompletionStage()

        override fun cancel(): CompletionStage<Void> = CompletableFuture.completedFuture(null)
    }
