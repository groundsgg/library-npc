package gg.grounds.npc.core.validation

/**
 * A stable machine code, input/JSON field path and human-readable explanation. Hosts may define
 * their own codes for integration-specific rejections.
 *
 * @property code Stable machine-readable category; hosts may define integration-specific codes.
 * @property path Input field/JSON path locating the failure, or $ for a whole-operation error.
 * @property message Human-readable failure description.
 */
public data class NpcProblem(val code: String, val path: String, val message: String)
