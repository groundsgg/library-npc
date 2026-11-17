package gg.grounds.npc.core.internal.content

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipFile

internal object ContentFiles {
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
            val bytes = read(remaining + 1)
            if (bytes.size > remaining)
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
            rejectZipLinks(path, maxEntries)
            ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) reserve(entry.name.removeSuffix("/"))
                    else
                        add(entry.name) { maximum ->
                            zip.getInputStream(entry).use { it.readNBytes(maximum) }
                        }
                }
            }
        }
        return result
    }

    private fun rejectZipLinks(path: Path, maxEntries: Int) {
        RandomAccessFile(path.toFile(), "r").use { file ->
            val length = file.length()
            if (length < 22 || length > 512L * 1024 * 1024)
                contentFailure(
                    "NPC_CONTENT_LIMIT",
                    path.toString(),
                    "Invalid or oversized resource archive",
                )
            val start = maxOf(0L, length - 65557)
            file.seek(start)
            val tail = ByteArray((length - start).toInt())
            file.readFully(tail)
            fun u16(bytes: ByteArray, offset: Int): Int =
                (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
            fun u32(bytes: ByteArray, offset: Int): Long =
                (0..3).fold(0L) { acc, i ->
                    acc or ((bytes[offset + i].toLong() and 255L) shl (8 * i))
                }
            val end =
                (tail.size - 22 downTo 0).firstOrNull {
                    u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tail.size
                }
                    ?: contentFailure(
                        "NPC_CONTENT_CONTRACT",
                        path.toString(),
                        "Missing ZIP directory",
                    )
            val count = u16(tail, end + 10)
            val directoryOffset = u32(tail, end + 16)
            val directorySize = u32(tail, end + 12)
            if (
                u16(tail, end + 4) != 0 ||
                    u16(tail, end + 6) != 0 ||
                    u16(tail, end + 8) != count ||
                    count > maxEntries ||
                    count == 65535 ||
                    directoryOffset + directorySize > start + end
            ) {
                contentFailure(
                    "NPC_CONTENT_LIMIT",
                    path.toString(),
                    "Unsupported or oversized ZIP directory",
                )
            }
            file.seek(directoryOffset)
            repeat(count) {
                val header = ByteArray(46)
                file.readFully(header)
                if (u32(header, 0) != 0x02014b50L)
                    contentFailure(
                        "NPC_CONTENT_CONTRACT",
                        path.toString(),
                        "Invalid ZIP directory entry",
                    )
                val mode = (u32(header, 38) ushr 16).toInt()
                if (mode and 0xf000 == 0xa000)
                    contentFailure(
                        "NPC_CONTENT_PATH",
                        path.toString(),
                        "ZIP symlink is not allowed",
                    )
                file.seek(file.filePointer + u16(header, 28) + u16(header, 30) + u16(header, 32))
                if (file.filePointer > directoryOffset + directorySize)
                    contentFailure(
                        "NPC_CONTENT_CONTRACT",
                        path.toString(),
                        "Truncated ZIP directory",
                    )
            }
        }
    }
}
