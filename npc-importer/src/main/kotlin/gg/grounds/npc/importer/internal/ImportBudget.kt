package gg.grounds.npc.importer.internal

/** Accounts retained output before it is copied, hashed or added to a content set. */
internal class ImportBudget(
    private val maxBytes: Int = 256 * 1024 * 1024,
    private val maxEntries: Int = 8192,
) {
    private val sizes = hashMapOf<String, Int>()
    private var used = 0L

    fun readLimit(name: String, perFile: Int, source: String): Int {
        checkEntry(name, source)
        val remaining = maxBytes - used + (sizes[name] ?: 0)
        if (remaining <= 0) exceeded(name, source)
        return minOf(perFile.toLong(), remaining).toInt()
    }

    fun charge(name: String, bytes: Int, source: String) {
        checkEntry(name, source)
        val total = used - (sizes[name] ?: 0) + bytes
        if (total > maxBytes) exceeded(name, source)
        used = total
        sizes[name] = bytes
    }

    fun exceeded(name: String, source: String): Nothing =
        fail(source, name, "Generated resource byte/entry limit exceeded", "NPC_IMPORT_LIMIT")

    private fun checkEntry(name: String, source: String) {
        if (name !in sizes && sizes.size >= maxEntries) exceeded(name, source)
    }
}
