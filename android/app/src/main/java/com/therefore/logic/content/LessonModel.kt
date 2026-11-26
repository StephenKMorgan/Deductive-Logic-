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
    /** Find a row where every formula is true (the set is satisfiable). */
    ALL_TRUE,
}

/** What a worlds or timeline explorer asks for, judged at the first world (w1) or at moment 0. */
enum class WorldsGoal {
    /** Every formula true. */
    ALL_TRUE,
    /** The (single) formula false. */
    FALSE,
}

/** How a worlds explorer reads its formulas: modal logic (□ ◇) or intuitionistic logic (states of knowledge). */
enum class KLogic { MODAL, INTUITIONISTIC }

enum class SolverGoal {
    /** Satisfy every clause. */
    SATISFY,
    /** Show that no assignment works: backtrack until no guess is left. */
    REFUTE,
}

enum class WorldGoal {
    /** Make every formula true. */
    ALL_TRUE,
    /** Make the (single) formula false. */
    FALSE,
    /** Every formula but the last true, the last false. */
    COUNTEREXAMPLE,
    /** Try every possible world (used to show two formulas always agree). */
    EVERY_WORLD,
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

    /**
     * A world explorer for predicate logic: three things (a, b, c) and properties to switch on and off,
     * with quantified statements evaluated live.
     */
    data class WorldStep(
        override val title: String,
        val prompt: String,
        val formulas: List<String>,
        val goal: WorldGoal,
        /** Label the formulas as premises and a conclusion. */
        val argument: Boolean = false,
        val success: String,
    ) : LessonStep

    /**
     * The solver board: guess letters, set forced ones, backtrack on conflicts (the DPLL algorithm by hand).
     * [propagateAll] offers the button that sets every forced letter at once; [maxGuesses] caps the guesses allowed.
     */
    data class SolverStep(
        override val title: String,
        val prompt: String,
        val clauses: List<String>,
        val goal: SolverGoal,
        val propagateAll: Boolean,
        val maxGuesses: Int? = null,
        val success: String,
    ) : LessonStep

    /** A truth tree for an argument: break formulas down, close branches, then say whether it's valid. */
    data class TreeStep(
        override val title: String,
        val prompt: String,
        val premises: List<String>,
        val conclusion: String,
        /** What the finished tree should show (checked by tests). */
        val valid: Boolean,
        val success: String,
    ) : LessonStep

    /**
     * Possible worlds: tap letters on and off in each world and (unless [fixedArrows]) draw arrows between them.
     * [frame] lists arrow patterns a model must have to count. With [impossible], the goal can't be met, and the
     * learner finishes by saying so after trying a few models.
     */
    data class KripkeStep(
        override val title: String,
        val prompt: String,
        val worlds: Int,
        val formulas: List<String>,
        val goal: WorldsGoal,
        val logic: KLogic = KLogic.MODAL,
        val arrows: Set<Pair<Int, Int>> = emptySet(),
        val fixedArrows: Boolean = false,
        val frame: Set<com.therefore.logic.ext.FrameProp> = emptySet(),
        val impossible: Boolean = false,
        val success: String,
    ) : LessonStep

    /** A timeline of moments: tap letters on and off at each moment, and watch temporal formulas at moment 0. */
    data class TimelineStep(
        override val title: String,
        val prompt: String,
        val moments: Int,
        val formulas: List<String>,
        val goal: WorldsGoal,
        /** Letters true at each moment to start with, like listOf("P", "PQ", ""). */
        val start: List<String> = emptyList(),
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
