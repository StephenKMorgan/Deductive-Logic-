package com.therefore.logic.data

import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.ProofLine
import com.therefore.logic.engine.Puzzle
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.Trap
import kotlinx.serialization.Serializable

@Serializable
data class Mistake(val text: String, val rule: Rule, val cites: List<Int>, val message: String, val trap: Trap)

enum class FeedbackKind { GOOD, BAD, HINT, INFO }

/** A hint's "fill it in" payload. */
@Serializable
data class Fill(val tokens: List<Char>, val rule: Rule, val cites: List<Int>)

@Serializable
data class Feedback(val kind: FeedbackKind, val text: String, val detail: String? = null, val fill: Fill? = null)

@Serializable
data class GameResult(val grade: String, val stars: Int, val score: Int, val modelLines: Int, val yourLines: Int, val seconds: Long)

/**
 * PRACTICE raises the course level. FREE replays a chosen level without changing it.
 * DAILY is the shared puzzle of the day. LESSON is a proof inside a lesson (not graded or saved).
 */
enum class Mode { PRACTICE, FREE, DAILY, LESSON }

/** Everything about the puzzle being solved. Saved after every change. */
@Serializable
data class GameState(
    val courseId: String,
    val level: Int,
    val mode: Mode = Mode.PRACTICE,
    /** For free play: the lesson whose rules the puzzles focus on. */
    val lessonId: String? = null,
    /** Set for the daily proof. */
    val date: String? = null,
    val puzzle: Puzzle,
    /** The rules offered on the board. */
    val rules: List<Rule> = emptyList(),
    val lines: List<ProofLine>,
    val tokens: List<Char> = emptyList(),
    val caret: Int = 0,
    val rule: Rule? = null,
    val cites: List<Int> = emptyList(),
    val mistakes: List<Mistake> = emptyList(),
    val hints: Int = 0,
    val hintPenalty: Int = 0,
    val hintStage: Int = 0,
    val hintTarget: Formula? = null,
    val revealed: Boolean = false,
    val done: Boolean = false,
    val startedAt: Long,
    val feedback: Feedback? = null,
    val result: GameResult? = null,
) {
    val premiseCount get() = puzzle.premises.size
    val isDaily get() = mode == Mode.DAILY
}
