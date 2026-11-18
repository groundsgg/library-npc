package gg.grounds.npc.cli.internal

import com.google.gson.Gson
import gg.grounds.npc.importer.error.NpcImportProblem
import java.io.PrintWriter

internal object CliDiagnostics {
    private val gson = Gson()

    fun problems(problems: List<NpcImportProblem>, json: Boolean, out: PrintWriter) {
        if (json) out.println(gson.toJson(mapOf("valid" to false, "problems" to problems)))
        else
            for (problem in problems) out.println(
                "${problem.source}:${problem.path}: ${problem.message} (code=${problem.code})"
            )
    }

    fun success(values: Map<String, Any>, json: Boolean, out: PrintWriter) {
        if (json) out.println(gson.toJson(values))
        else out.println(values.entries.joinToString(" ") { "${it.key}=${it.value}" })
    }
}
