package gg.grounds.npc.core.validation

/** Stable machine-readable code, JSON field path and human-readable explanation of an invalid input or rejected operation. */
data class NpcProblem(val code: String, val path: String, val message: String)
