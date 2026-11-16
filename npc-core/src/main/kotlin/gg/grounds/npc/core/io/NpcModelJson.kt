package gg.grounds.npc.core.io

import gg.grounds.npc.core.internal.NpcModelParser
import gg.grounds.npc.core.internal.NpcValidationException
import gg.grounds.npc.core.validation.NpcProblem
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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
            NpcDecodeResult.Success(NpcModelParser.parse(json, limits))
        } catch (e: NpcValidationException) {
            NpcDecodeResult.Failure(
                listOf(NpcProblem("NPC_MODEL_INVALID", e.path, e.message ?: "Invalid model"))
            )
        } catch (e: java.nio.charset.CharacterCodingException) {
            NpcDecodeResult.Failure(
                listOf(NpcProblem("NPC_MODEL_INVALID_UTF8", "$", "Model must contain valid UTF-8"))
            )
        }
    }
}
