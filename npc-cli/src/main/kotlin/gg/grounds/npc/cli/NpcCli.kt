package gg.grounds.npc.cli

import gg.grounds.npc.cli.internal.*
import gg.grounds.npc.importer.NpcModelImporter
import gg.grounds.npc.importer.error.*
import java.io.IOException
import java.io.PrintWriter
import kotlin.system.exitProcess

/** Standalone model importer; process exit is confined to [main]. */
public object NpcCli {
    /** Execute the CLI with normal process streams and exit with its documented status. */
    @JvmStatic
    public fun main(args: Array<String>): Unit =
        exitProcess(execute(args, PrintWriter(System.out, true), PrintWriter(System.err, true)))

    /**
     * Execute without owning the process or caller streams. Returns 0 success, 2 usage, 3 authored
     * content, 4 IO, or 1 internal failure. Flushes output but never closes the supplied writers.
     */
    @JvmStatic
    public fun execute(args: Array<String>, out: PrintWriter, err: PrintWriter): Int {
        val json = "--json" in args
        val debug = "--debug" in args
        try {
            val command = CliArguments.parse(args)
            when (command.command) {
                "help" -> {
                    out.println(
                        """NPC model importer
Usage: npc-cli <validate|compile> --input <file|directory> --namespace <namespace> --target <26.2|26.3>
compile requires --output <new-directory>
Options: --base-pack <zip|directory> --config <json> --description <text> --json --debug
Other commands: --help --version
Output: server-content.zip, resourcepack.zip, pack-fragment/, manifest.json
Exit codes: 0 success, 2 usage, 3 invalid content, 4 IO, 1 internal failure"""
                    )
                    return 0
                }
                "version" -> {
                    out.println(NpcModelImporter.version())
                    return 0
                }
                "validate" -> {
                    val problems = NpcModelImporter.validate(command.options!!)
                    if (problems.isNotEmpty()) {
                        CliDiagnostics.problems(problems, json, err)
                        return 3
                    }
                    CliDiagnostics.success(
                        mapOf("valid" to true, "problems" to emptyList<Any>()),
                        json,
                        out,
                    )
                    return 0
                }
                else -> {
                    val output = NpcModelImporter.compile(command.options!!, command.output!!)
                    CliDiagnostics.success(
                        mapOf(
                            "contentId" to output.contentId,
                            "serverContent" to output.serverContent.toString(),
                            "resourcePack" to output.resourcePack.toString(),
                        ),
                        json,
                        out,
                    )
                    return 0
                }
            }
        } catch (e: CliUsageException) {
            CliDiagnostics.problems(
                listOf(
                    NpcImportProblem(
                        "NPC_CLI_USAGE",
                        "arguments",
                        "$",
                        e.message ?: "Invalid arguments",
                    )
                ),
                json,
                err,
            )
            return 2
        } catch (e: NpcImportException) {
            CliDiagnostics.problems(e.problems, json, err)
            if (debug) e.printStackTrace(err)
            return 3
        } catch (e: IOException) {
            CliDiagnostics.problems(
                listOf(
                    NpcImportProblem(
                        "NPC_CLI_IO",
                        "filesystem",
                        "$",
                        e.message ?: "Cannot read/write configured input/output",
                    )
                ),
                json,
                err,
            )
            if (debug) e.printStackTrace(err)
            return 4
        } catch (e: Exception) {
            CliDiagnostics.problems(
                listOf(
                    NpcImportProblem(
                        "NPC_CLI_INTERNAL",
                        "importer",
                        "$",
                        e.message ?: "Unexpected importer failure",
                    )
                ),
                json,
                err,
            )
            if (debug) e.printStackTrace(err)
            return 1
        } finally {
            out.flush()
            err.flush()
        }
    }
}
