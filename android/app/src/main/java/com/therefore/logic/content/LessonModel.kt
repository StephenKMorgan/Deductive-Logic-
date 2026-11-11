package com.therefore.logic.content

import com.therefore.logic.data.GameState
import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.Parser
import com.therefore.logic.engine.Rule

/** A worked example: lines of formula + justification. Indented lines are inside an assumption. */
data class Example(val lines: List<Pair<String, String>>, val conclusion: String, val depths: List<Int> = emptyList())

enum class TableGoal {
    /** Find the row that makes the (single) formula false. */
    FALSE_ROW,
    /** Find a row where every premise is true but the conclusion is false. */
    COUNTEREXAMPLE,
    /** Visit every row (used to show that formulas always agree, or that no counterexample exists). */
    ALL_ROWS,
}

/** A coaching instruction in a guided proof. The step is done once `done` is true for the board. */
class Coach(val text: String, val done: (GameState) -> Boolean)

sealed interface LessonStep {
    val title: String

    data class Read(override val title: String, val body: List<String>, val example: Example? = null, val rules: List<Rule> = emptyList()) : LessonStep

    data class Quiz(override val title: String, val question: String, val options: List<String>, val answer: Int, val explain: String) : LessonStep

    /** A truth-table explorer: flip the letters and watch the formulas. */
    data class Table(
        override val title: String,
        val prompt: String,
        val formulas: List<String>,
        val goal: TableGoal,
        /** For COUNTEREXAMPLE / ALL_ROWS-on-an-argument: the last formula is the conclusion. */
        val argument: Boolean = false,
        val success: String,
    ) : LessonStep

    /** A real proof with a coach that moves on as the learner does each thing. */
    data class Guided(
        override val title: String,
        val premises: List<String>,
        val conclusion: String,
        val rules: List<Rule>,
        val coach: List<Coach>,
        val success: String,
        /** A model proof: (formula, justification) per line after the premises, e.g. "1, 2, MP" or "ACP". */
        val solution: List<Pair<String, String>>,
    ) : LessonStep

    /** A fixed proof to finish on your own, with hints available. */
    data class Practice(
        override val title: String,
        val prompt: String,
        val premises: List<String>,
        val conclusion: String,
        val rules: List<Rule>,
        val solution: List<Pair<String, String>>,
    ) : LessonStep
}

data class LessonDef(
    val id: String,
    val title: String,
    val summary: String,
    /** The rules free play focuses on for this lesson (empty = the whole course). */
    val rules: List<Rule>,
    val steps: List<LessonStep>,
)

// ----- Helpers for writing coach conditions -----

fun f(s: String): Formula = Parser.parseOrNull(s) ?: error("Bad formula in lesson: $s")

fun GameState.has(s: String) = f(s).let { target -> lines.any { it.formula == target } }
fun GameState.hasAssumed(s: String, rule: Rule) = f(s).let { target -> lines.any { it.formula == target && it.rule == rule } }
fun GameState.typed(s: String) = tokens.joinToString("") == f(s).show().filter { !it.isWhitespace() }
fun GameState.citing(vararg n: Int) = cites.toSet() == n.toSet()

/** Builds an engine puzzle (with the model proof used for hints) from a lesson's written solution. */
fun lessonPuzzle(premises: List<String>, conclusion: String, solution: List<Pair<String, String>>): com.therefore.logic.engine.Puzzle {
    val lines = premises.map(::f).toMutableList()
    val steps = solution.map { (fs, just) ->
        val parts = just.split(",").map { it.trim() }
        val rule = Rule.valueOf(parts.last())
        val from = parts.dropLast(1).map { lines[it.toInt() - 1] }
        val formula = f(fs)
        lines += formula
        com.therefore.logic.engine.Step(formula, rule, from)
    }
    return com.therefore.logic.engine.Puzzle(premises.map(::f), f(conclusion), steps)
}

fun LessonStep.Guided.puzzle() = lessonPuzzle(premises, conclusion, solution)
fun LessonStep.Practice.puzzle() = lessonPuzzle(premises, conclusion, solution)
