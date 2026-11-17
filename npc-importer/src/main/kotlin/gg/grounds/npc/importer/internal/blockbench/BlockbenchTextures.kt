package gg.grounds.npc.importer.internal.blockbench

import com.google.gson.JsonObject
import gg.grounds.npc.importer.internal.*
import java.io.ByteArrayInputStream
import java.nio.file.*
import java.util.Base64
import javax.imageio.ImageIO

internal class BlockbenchTextures(
    private val context: ImportContext,
    private val source: Path,
    private val inputRoot: Path,
    private val id: String,
) {
    fun read(root: JsonObject, resources: MutableMap<String, ByteArray>): List<String> {
        val textures = root.getAsJsonArray("textures") ?: error("Missing textures")
        require(textures.size() <= 128) { "Too many textures" }
        val namespace = id.substringBefore(':')
        val suffix = id.substringAfter(':').removePrefix("npc/")
        return textures.mapIndexed { index, raw ->
            context.at("$.textures[$index]") {
                val texture = raw.asJsonObject
                val sourceValue = texture["source"]?.asString
                val bytes =
                    if (sourceValue != null && sourceValue.startsWith("data:image/png;base64,")) {
                        Base64.getDecoder().decode(sourceValue.substringAfter(','))
                    } else {
                        require(sourceValue == null || !sourceValue.contains("://")) {
                            "Network textures are unsupported"
                        }
                        val relative =
                            texture["relative_path"]?.asString
                                ?: sourceValue
                                ?: error("Missing local PNG texture")
                        val child = Path.of(relative)
                        require(!child.isAbsolute && ':' !in relative && '\\' !in relative) {
                            "Texture path must be relative"
                        }
                        val path = source.parent.resolve(child).normalize().toAbsolutePath()
                        require(path.startsWith(inputRoot)) { "Texture escapes input directory" }
                        var parent: Path? = path
                        while (parent != null && parent.startsWith(inputRoot)) {
                            require(!Files.isSymbolicLink(parent)) {
                                "Texture symlink is unsupported"
                            }
                            parent = parent.parent
                        }
                        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            "Texture file is missing"
                        }
                        Files.newInputStream(path).use { it.readNBytes(16 * 1024 * 1024 + 1) }
                    }
                validate(bytes)
                val textureName =
                    "texture_${index.toString().padStart(3,'0')}_${ImportJson.hash(bytes)}"
                val path = "item/npc/$suffix/$textureName"
                resources["assets/$namespace/textures/$path.png"] = bytes
                "$namespace:$path"
            }
        }
    }

    private fun validate(bytes: ByteArray) {
        require(
            bytes.size in 8..16 * 1024 * 1024 &&
                bytes
                    .copyOfRange(0, 8)
                    .contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
        ) {
            "Texture must be a bounded PNG"
        }
        javax.imageio.stream.MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReadersByFormatName("png").next()
            try {
                reader.input = input
                require(reader.getWidth(0) in 1..4096 && reader.getHeight(0) in 1..4096) {
                    "Texture dimensions exceed 4096 pixels"
                }
                reader.read(0) ?: error("Invalid PNG image")
            } finally {
                reader.dispose()
            }
        }
    }
}
