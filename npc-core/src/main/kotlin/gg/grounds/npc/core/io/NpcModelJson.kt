package gg.grounds.npc.core.io

import gg.grounds.npc.core.internal.NpcModelParser
import gg.grounds.npc.core.internal.NpcValidationException
import gg.grounds.npc.core.validation.NpcProblem
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Bounded UTF-8 schema-1 decoder. Expected authored errors become structured diagnostics with field
 * paths; a failure never returns a partially usable model.
 */
public object NpcModelJson {
    /**
     * Decode bounded UTF-8 schema-1 bytes into a complete validated model. Authored syntax/semantic
     * errors return Failure with a field path; invalid UTF-8 and byte overflow have distinct codes.
     * Limits may be stricter than the hard model maxima.
     */
    @JvmStatic
    @JvmOverloads
    public fun decode(
        bytes: ByteArray,
        limits: NpcDecodeLimits = NpcDecodeLimits(),
    ): NpcDecodeResult {
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
