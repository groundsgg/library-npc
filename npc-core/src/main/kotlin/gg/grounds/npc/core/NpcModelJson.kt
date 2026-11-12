package gg.grounds.npc.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class NpcDecodeLimits
@JvmOverloads
constructor(
    val maxBytes: Int = 2 * 1024 * 1024,
    val maxBones: Int = 256,
    val maxParts: Int = 1024,
    val maxClips: Int = 128,
    val maxKeysPerTrack: Int = 4096,
    val maxClipSeconds: Double = 300.0,
) {
    init {
        require(maxBytes > 0 && maxBones > 0 && maxParts > 0 && maxClips > 0 && maxKeysPerTrack > 0)
        require(maxClipSeconds.isFinite() && maxClipSeconds > 0)
    }
}

data class NpcProblem(val code: String, val path: String, val message: String)

sealed interface NpcDecodeResult {
    data class Success(val model: NpcModel) : NpcDecodeResult

    class Failure(problems: Collection<NpcProblem>) : NpcDecodeResult {
        val problems = immutableList(problems)

        init {
            require(this.problems.isNotEmpty())
        }
    }
}

object NpcModelJson {
    @JvmStatic
    @JvmOverloads
    fun decode(bytes: ByteArray, limits: NpcDecodeLimits = NpcDecodeLimits()): NpcDecodeResult {
        if (bytes.size > limits.maxBytes)
            return NpcDecodeResult.Failure(
                listOf(NpcProblem("NPC_MODEL_TOO_LARGE", "$", "Model exceeds the byte limit"))
            )
        return try {
            val json =
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            NpcDecodeResult.Success(NpcModel.parse(json, limits))
        } catch (e: Exception) {
            NpcDecodeResult.Failure(
                listOf(NpcProblem("NPC_MODEL_INVALID", "$", e.message ?: "Invalid model"))
            )
        }
    }
}
