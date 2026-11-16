package gg.grounds.npc.core.internal

/** Unambiguous paths shared by syntax and semantic diagnostics. */
internal object NpcJsonPath {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun member(parent: String, key: String): String {
        if (identifier.matches(key)) return "$parent.$key"
        val quoted = buildString {
            append('"')
            for (character in key) when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else ->
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else append(character)
            }
            append('"')
        }
        return "$parent[$quoted]"
    }
}
