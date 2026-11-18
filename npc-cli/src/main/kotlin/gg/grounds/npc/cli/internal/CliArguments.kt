package gg.grounds.npc.cli.internal

import gg.grounds.npc.importer.config.*
import java.nio.file.Path

internal class CliUsageException(message: String) : IllegalArgumentException(message)

internal data class CliArguments(
    val command: String,
    val options: NpcImportOptions?,
    val output: Path?,
    val json: Boolean,
    val debug: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): CliArguments {
            if (
                args.contentEquals(arrayOf("--help")) ||
                    args.contentEquals(arrayOf("-h")) ||
                    args.isEmpty()
            )
                return CliArguments("help", null, null, false, false)
            if (args.contentEquals(arrayOf("--version")))
                return CliArguments("version", null, null, false, false)
            val command = args[0]
            if (command !in setOf("compile", "validate"))
                throw CliUsageException("Expected compile or validate; use --help")
            if (args.size == 2 && args[1] in setOf("--help", "-h"))
                return CliArguments("help", null, null, false, false)
            val values = linkedMapOf<String, String>()
            val flags = hashSetOf<String>()
            val valued =
                setOf(
                    "--input",
                    "--namespace",
                    "--target",
                    "--output",
                    "--base-pack",
                    "--config",
                    "--description",
                )
            var index = 1
            while (index < args.size) {
                val key = args[index++]
                if (key in setOf("--json", "--debug")) {
                    if (!flags.add(key)) throw CliUsageException("Duplicate option: $key")
                } else {
                    if (key !in valued) throw CliUsageException("Unknown option: $key")
                    if (values.containsKey(key)) throw CliUsageException("Duplicate option: $key")
                    if (index == args.size || args[index].startsWith("--"))
                        throw CliUsageException("Missing value for $key")
                    values[key] = args[index++]
                }
            }
            fun required(name: String) =
                values[name] ?: throw CliUsageException("Missing required option $name")
            try {
                if (command == "validate" && "--output" in values)
                    throw CliUsageException("validate writes no output; remove --output")
                val builder =
                    NpcImportOptions.builder(
                        Path.of(required("--input")),
                        required("--namespace"),
                        NpcMinecraftTarget.fromVersion(required("--target")),
                    )
                values["--base-pack"]?.let { builder.basePack(Path.of(it)) }
                values["--config"]?.let { builder.config(Path.of(it)) }
                values["--description"]?.let { builder.description(it) }
                val output = if (command == "compile") Path.of(required("--output")) else null
                return CliArguments(
                    command,
                    builder.build(),
                    output,
                    "--json" in flags,
                    "--debug" in flags,
                )
            } catch (e: IllegalArgumentException) {
                if (e is CliUsageException) throw e
                throw CliUsageException(e.message ?: "Invalid command option")
            }
        }
    }
}
