package gg.grounds.npc.core.internal.content

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import gg.grounds.npc.core.io.content.NpcContentException
import gg.grounds.npc.core.validation.NpcProblem
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal fun contentFailure(code: String, path: String, message: String): Nothing =
    throw NpcContentException(listOf(NpcProblem(code, path, message)))

internal object ContentJson {
    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun canonical(value: JsonElement): String =
        when {
            value.isJsonObject ->
                value.asJsonObject.keySet().sorted().joinToString(",", "{", "}") {
                    JsonPrimitive(it).toString() + ":" + canonical(value.asJsonObject[it])
                }
            value.isJsonArray -> value.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
            else -> value.toString()
        }

    fun parse(bytes: ByteArray, path: String): JsonObject =
        at(path) {
            if (bytes.size > 4 * 1024 * 1024)
                contentFailure("NPC_CONTENT_LIMIT", path, "JSON resource exceeds 4 MiB")
            val text =
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            JsonReader(StringReader(text)).use { reader ->
                reader.strictness = Strictness.STRICT
                var nodes = 0
                fun read(depth: Int): JsonElement {
                    require(depth <= 64 && ++nodes <= 200000) {
                        "JSON nesting or node limit exceeded"
                    }
                    return when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT ->
                            JsonObject().also { obj ->
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val key = reader.nextName()
                                    require(!obj.has(key)) { "Duplicate JSON key: $key" }
                                    obj.add(key, read(depth + 1))
                                }
                                reader.endObject()
                            }
                        JsonToken.BEGIN_ARRAY ->
                            JsonArray().also { array ->
                                reader.beginArray()
                                while (reader.hasNext()) array.add(read(depth + 1))
                                reader.endArray()
                            }
                        JsonToken.STRING -> JsonPrimitive(reader.nextString())
                        JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
                        JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                        JsonToken.NULL -> {
                            reader.nextNull()
                            JsonNull.INSTANCE
                        }
                        else -> error("Unexpected JSON token")
                    }
                }
                val root = read(0).asJsonObject
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON input" }
                root
            }
        }

    fun <T> at(path: String, body: () -> T): T =
        try {
            body()
        } catch (e: NpcContentException) {
            throw e
        } catch (e: Exception) {
            contentFailure("NPC_CONTENT_CONTRACT", path, e.message ?: "Invalid content contract")
        }

    fun integer(value: JsonElement): Int {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.intValueExact()
    }

    fun digest(value: JsonElement): String =
        value.asString.also { require(it.matches(Regex("[a-f0-9]{64}"))) { "Invalid SHA-256" } }
}
