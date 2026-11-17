package gg.grounds.npc.importer.internal

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import gg.grounds.npc.importer.error.NpcImportException
import gg.grounds.npc.importer.error.NpcImportProblem
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal fun fail(
    source: String,
    path: String,
    message: String,
    code: String = "NPC_IMPORT_INVALID",
): Nothing = throw NpcImportException(listOf(NpcImportProblem(code, source, path, message)))

internal class ImportContext(val source: String) {
    fun <T> at(path: String, read: () -> T): T =
        try {
            read()
        } catch (e: NpcImportException) {
            throw e
        } catch (e: Exception) {
            fail(source, path, e.message ?: "Invalid input")
        }

    fun number(value: JsonElement?, path: String, default: Double? = null): Double =
        at(path) {
            if (value == null) return@at default ?: error("Missing numeric value")
            require(value.isJsonPrimitive && !value.asJsonPrimitive.isBoolean) {
                "Expected a finite numeric constant"
            }
            value.asString.toDouble().also {
                require(it.isFinite()) { "Expected a finite numeric constant" }
            }
        }

    fun vector(
        value: JsonElement?,
        path: String,
        default: List<Double> = listOf(0.0, 0.0, 0.0),
        divisor: Double = 1.0,
    ): List<Double> =
        at(path) {
            if (value == null) return@at default
            require(value.isJsonArray && value.asJsonArray.size() == 3) { "Expected an XYZ vector" }
            value.asJsonArray.mapIndexed { i, v -> number(v, "$path[$i]") / divisor }
        }

    fun parse(bytes: ByteArray): JsonObject =
        at("$") {
            require(bytes.size <= 16 * 1024 * 1024) { "Source exceeds 16 MiB" }
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
                    require(depth <= 64 && ++nodes <= 200000) { "JSON nesting/node limit exceeded" }
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
                            JsonArray().also { arr ->
                                reader.beginArray()
                                while (reader.hasNext()) arr.add(read(depth + 1))
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
                read(0).asJsonObject.also {
                    require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON input" }
                }
            }
        }
}

internal object ImportJson {
    val gson = Gson()

    fun objectOf(vararg fields: Pair<String, Any?>): JsonObject =
        JsonObject().apply { for ((key, value) in fields) add(key, gson.toJsonTree(value)) }

    fun canonical(value: JsonElement): String =
        when {
            value.isJsonObject ->
                value.asJsonObject.keySet().sorted().joinToString(",", "{", "}") {
                    JsonPrimitive(it).toString() + ":" + canonical(value.asJsonObject[it])
                }
            value.isJsonArray -> value.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
            else -> value.toString()
        }

    fun bytes(value: JsonElement): ByteArray = (canonical(value) + "\n").toByteArray()

    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
