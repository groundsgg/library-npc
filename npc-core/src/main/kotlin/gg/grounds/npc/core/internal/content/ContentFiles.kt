package gg.grounds.npc.core.internal.content

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream

internal object ContentFiles {
    private fun resourceLimit(name: String): Int =
        if (
            name == "bundle.json" ||
                name == "pack.mcmeta" ||
                name.matches(Regex("(?:[^/]+/)?assets/[^/]+/sounds\\.json"))
        )
            4 * 1024 * 1024
        else Int.MAX_VALUE

    fun snapshot(resources: Map<String, ByteArray>, limit: Int): Map<String, ByteArray> {
        if (resources.size > 8192) contentFailure("NPC_CONTENT_LIMIT", "$", "Too many resources")
        var size = 0L
        val names = hashSetOf<String>()
        for ((name, bytes) in resources) {
            safeName(name)
            if (bytes.size > resourceLimit(name))
                contentFailure("NPC_CONTENT_LIMIT", name, "JSON resource exceeds 4 MiB")
            if (!names.add(name.lowercase(Locale.ROOT)))
                contentFailure("NPC_CONTENT_DUPLICATE", name, "Case-ambiguous resource")
            size += bytes.size
            if (size > limit)
                contentFailure("NPC_CONTENT_LIMIT", name, "Resource byte limit exceeded")
        }
        return resources.mapValues { it.value.copyOf() }
    }

    fun safeName(name: String): String {
        if (
            name.isEmpty() ||
                name.length > 1024 ||
                name.startsWith('/') ||
                '\\' in name ||
                ':' in name ||
                name.any { it.code < 32 } ||
                name.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
                Normalizer.normalize(name, Normalizer.Form.NFC) != name
        ) {
            contentFailure("NPC_CONTENT_PATH", name, "Unsafe or ambiguous resource path")
        }
        return name
    }

    fun read(path: Path, limit: Int, maxEntries: Int = 8192): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        val names = hashSetOf<String>()
        var total = 0
        fun reserve(name: String) {
            safeName(name)
            if (!names.add(name.lowercase(Locale.ROOT)))
                contentFailure(
                    "NPC_CONTENT_DUPLICATE",
                    name,
                    "Duplicate or case-ambiguous resource",
                )
            if (names.size > maxEntries)
                contentFailure("NPC_CONTENT_LIMIT", name, "Too many resources")
        }
        fun add(name: String, read: (Int) -> ByteArray) {
            reserve(name)
            val remaining = limit - total
            val maximum = minOf(remaining, resourceLimit(name))
            val bytes = read(maximum + 1)
            if (bytes.size > maximum)
                contentFailure("NPC_CONTENT_LIMIT", name, "Expanded resource byte limit exceeded")
            total += bytes.size
            result[name] = bytes
        }
        if (Files.isSymbolicLink(path))
            contentFailure("NPC_CONTENT_PATH", path.toString(), "Symlink input is not allowed")
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
            Files.walk(path).use { stream ->
                stream
                    .filter { it != path }
                    .forEach { file ->
                        val name = path.relativize(file).joinToString("/")
                        if (Files.isSymbolicLink(file))
                            contentFailure(
                                "NPC_CONTENT_PATH",
                                name,
                                "Symlink resource is not allowed",
                            )
                        if (Files.isDirectory(file, NOFOLLOW_LINKS)) reserve(name)
                        else {
                            if (!Files.isRegularFile(file, NOFOLLOW_LINKS))
                                contentFailure("NPC_CONTENT_PATH", name, "Not a regular resource")
                            add(name) { maximum ->
                                Files.newInputStream(file).use { it.readNBytes(maximum) }
                            }
                        }
                    }
            }
        } else {
            FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel ->
                val expected = readDirectory(channel, maxEntries)
                channel.position(0)
                val consumed = hashSetOf<String>()
                ZipInputStream(Channels.newInputStream(channel)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val description =
                            expected[entry.name]
                                ?: contentFailure(
                                    "NPC_CONTENT_CONTRACT",
                                    entry.name,
                                    "Unindexed ZIP entry",
                                )
                        if (!consumed.add(entry.name))
                            contentFailure(
                                "NPC_CONTENT_DUPLICATE",
                                entry.name,
                                "Duplicate ZIP entry",
                            )
                        if (entry.isDirectory) {
                            reserve(entry.name.removeSuffix("/"))
                            if (zip.read() != -1)
                                contentFailure(
                                    "NPC_CONTENT_CONTRACT",
                                    entry.name,
                                    "Directory has a payload",
                                )
                        } else add(entry.name) { maximum -> zip.readNBytes(maximum) }
                        zip.closeEntry()
                        if (
                            entry.method != description.method ||
                                entry.crc != description.crc ||
                                entry.size != description.size ||
                                entry.compressedSize != description.compressedSize
                        )
                            contentFailure(
                                "NPC_CONTENT_CONTRACT",
                                entry.name,
                                "ZIP entry differs from validated directory",
                            )
                    }
                }
                if (consumed != expected.keys)
                    contentFailure("NPC_CONTENT_CONTRACT", "$", "Missing indexed ZIP entry")
            }
        }

        return result
    }

    private data class ZipDescription(
        val method: Int,
        val crc: Long,
        val size: Long,
        val compressedSize: Long,
    )

    // Validation and decompression share one open descriptor. Replacing the pathname cannot
    // switch the archive, and every consumed local entry must match this validated directory.
    private fun readDirectory(channel: FileChannel, maxEntries: Int): Map<String, ZipDescription> {
        fun readAt(position: Long, count: Int): ByteArray {
            channel.position(position)
            val bytes = ByteArray(count)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0)
                throw EOFException("Truncated ZIP directory")
            return bytes
        }
        fun u16(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        fun u32(bytes: ByteArray, offset: Int): Long =
            (0..3).fold(0L) { acc, i -> acc or ((bytes[offset + i].toLong() and 255L) shl (8 * i)) }
        val length = channel.size()
        if (length < 22 || length > 512L * 1024 * 1024)
            contentFailure("NPC_CONTENT_LIMIT", "$", "Invalid or oversized resource archive")
        val start = maxOf(0L, length - 65557)
        val tail = readAt(start, (length - start).toInt())
        val end =
            (tail.size - 22 downTo 0).firstOrNull {
                u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tail.size
            } ?: contentFailure("NPC_CONTENT_CONTRACT", "$", "Missing ZIP directory")
        val count = u16(tail, end + 10)
        val offset = u32(tail, end + 16)
        val size = u32(tail, end + 12)
        if (
            u16(tail, end + 4) != 0 ||
                u16(tail, end + 6) != 0 ||
                u16(tail, end + 8) != count ||
                count > maxEntries ||
                count == 65535 ||
                offset + size != start + end
        )
            contentFailure("NPC_CONTENT_LIMIT", "$", "Unsupported or oversized ZIP directory")
        var cursor = offset
        val result = linkedMapOf<String, ZipDescription>()
        repeat(count) {
            if (cursor + 46 > offset + size)
                contentFailure("NPC_CONTENT_CONTRACT", "$", "Truncated ZIP directory")
            val header = readAt(cursor, 46)
            if (u32(header, 0) != 0x02014b50L)
                contentFailure("NPC_CONTENT_CONTRACT", "$", "Invalid ZIP directory entry")
            val nameSize = u16(header, 28)
            val next = cursor + 46 + nameSize + u16(header, 30) + u16(header, 32)
            if (nameSize !in 1..1025 || next > offset + size)
                contentFailure("NPC_CONTENT_CONTRACT", "$", "Invalid ZIP directory entry size")
            val name =
                ContentJson.at("$") {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(readAt(cursor + 46, nameSize)))
                        .toString()
                }
            safeName(name.removeSuffix("/"))
            val mode = (u32(header, 38) ushr 16).toInt()
            if (mode and 0xf000 == 0xa000)
                contentFailure("NPC_CONTENT_PATH", name, "ZIP symlink is not allowed")
            val description =
                ZipDescription(u16(header, 10), u32(header, 16), u32(header, 24), u32(header, 20))
            if (result.put(name, description) != null)
                contentFailure("NPC_CONTENT_DUPLICATE", name, "Duplicate ZIP directory entry")
            cursor = next
        }
        if (cursor != offset + size)
            contentFailure("NPC_CONTENT_CONTRACT", "$", "Unvalidated ZIP directory entries")
        return result
    }
}
