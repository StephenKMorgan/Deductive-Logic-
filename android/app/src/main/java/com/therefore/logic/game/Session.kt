package com.therefore.logic.game

import com.therefore.logic.data.Feedback
import com.therefore.logic.data.FeedbackKind
import com.therefore.logic.data.Fill
import com.therefore.logic.data.GameResult
import com.therefore.logic.data.GameState
import com.therefore.logic.data.Grades
import com.therefore.logic.data.Mistake
import com.therefore.logic.data.Mode
import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.ParseResult
import com.therefore.logic.engine.Parser
import com.therefore.logic.engine.ProofLine
import com.therefore.logic.engine.Proofs
import com.therefore.logic.engine.Puzzle
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.RuleKind
import com.therefore.logic.engine.Trap

/** What a move should feel like on the phone. */
enum class Effect { KEY, ACCEPT, REJECT, HINT, SOLVED }

data class Outcome(val state: GameState, val effect: Effect? = null)

/** Everything a proof board can do. Implemented by the game screen and by guided lessons. */
interface BoardActions {
    fun key(t: Char)
    fun backspace()
    fun moveCaret(delta: Int)
    fun setCaret(i: Int)
    fun toggleCite(n: Int)
    fun copyLine(n: Int)
    fun selectRule(r: Rule?)
    fun fillHint()
    fun addLine()
    fun undoLine()
    fun hint()
}

/** The rules of play, as pure functions from one board state to the next. */
object Session {
    private val HINT_COST = intArrayOf(0, 4, 8, 15)

    /** The most one quiet stretch can add to a puzzle's time: thinking counts, wandering off doesn't. */
    const val IDLE_CAP_MS = 5 * 60_000L

    /** Adds the time since the last tick (capped) and restarts the clock. */
    fun tick(s: GameState, now: Long = System.currentTimeMillis()): GameState {
        if (s.done) return s
        val last = s.lastTick ?: return s.copy(lastTick = now)
        return s.copy(activeMs = s.activeMs + (now - last).coerceIn(0, IDLE_CAP_MS), lastTick = now)
    }

    /** Stops the clock, e.g. when the app goes to the background. */
    fun pause(s: GameState, now: Long = System.currentTimeMillis()): GameState =
        if (s.done || s.lastTick == null) s else tick(s, now).copy(lastTick = null)

    /** Starts the clock again without counting the paused time. */
    fun resume(s: GameState, now: Long = System.currentTimeMillis()): GameState =
        if (s.done || s.lastTick != null) s else s.copy(lastTick = now)

    fun newGame(courseId: String, level: Int, mode: Mode, puzzle: Puzzle, rules: List<Rule>, date: String? = null, lessonId: String? = null) =
        GameState(
            courseId = courseId, level = level, mode = mode, lessonId = lessonId, date = date, puzzle = puzzle, rules = rules,
            lines = puzzle.premises.map { ProofLine(it, null) }, startedAt = System.currentTimeMillis(),
        ).let { it.copy(lastTick = it.startedAt) }

    /** Clears a stale "line accepted" message once the next line is started. */
    private fun GameState.touched() =
        if (feedback?.kind == FeedbackKind.GOOD || feedback?.kind == FeedbackKind.INFO) copy(feedback = null) else this

    fun key(s: GameState, t: Char) = Outcome(
        s.touched().copy(tokens = s.tokens.toMutableList().apply { add(s.caret, t) }, caret = s.caret + 1), Effect.KEY,
    )

    fun backspace(s: GameState) = if (s.caret == 0) Outcome(s) else Outcome(
        s.touched().copy(tokens = s.tokens.toMutableList().apply { removeAt(s.caret - 1) }, caret = s.caret - 1), Effect.KEY,
    )

    fun setCaret(s: GameState, i: Int) = Outcome(s.touched().copy(caret = i.coerceIn(0, s.tokens.size)))

    fun toggleCite(s: GameState, n: Int) = Outcome(s.touched().copy(cites = if (n in s.cites) s.cites - n else s.cites + n), Effect.KEY)

    fun copyLine(s: GameState, n: Int): Outcome {
        val tokens = s.lines[n - 1].formula.show().filter { !it.isWhitespace() }.toList()
        return Outcome(s.touched().copy(tokens = tokens, caret = tokens.size))
    }

    /** Picking ACP, AIP, CP or IP with an empty input fills in the line that rule most likely wants. */
    fun selectRule(s: GameState, r: Rule?): Outcome {
        // If the input still holds what the previous rule filled in, it isn't the player's work: replace it.
        val prevFill = s.rule?.let { Proofs.suggestion(s.puzzle.conclusion, s.lines, it) }?.first
        val untouchedFill = prevFill != null && s.tokens.joinToString("") == prevFill.show().filter { !it.isWhitespace() }
        val base = if (untouchedFill) s.touched().copy(rule = r, tokens = emptyList(), caret = 0, feedback = null) else s.touched().copy(rule = r)
        if (r == null || base.tokens.isNotEmpty()) return Outcome(base)
        val (f, note) = Proofs.suggestion(s.puzzle.conclusion, s.lines, r) ?: return Outcome(base)
        val tokens = f.show().filter { !it.isWhitespace() }.toList()
        return Outcome(base.copy(tokens = tokens, caret = tokens.size, feedback = Feedback(FeedbackKind.INFO, note)))
    }

    fun fillHint(s: GameState): Outcome {
        val f = s.feedback?.fill ?: return Outcome(s)
        return Outcome(s.copy(tokens = f.tokens, caret = f.tokens.size, rule = f.rule, cites = f.cites))
    }

    private fun bad(s: GameState, text: String) = Outcome(s.copy(feedback = Feedback(FeedbackKind.BAD, text)), Effect.REJECT)

    fun addLine(s: GameState): Outcome {
        val formula = when (val p = Parser.parse(s.tokens.joinToString(""))) {
            is ParseResult.Ok -> p.formula
            is ParseResult.Error -> return bad(s, p.message)
        }
        val rule = s.rule ?: return bad(s, "Pick the rule that justifies this line.")
        val sc = Proofs.scopes(s.lines)
        if (rule.kind != RuleKind.CLOSE) {
            val dup = s.lines.indices.firstOrNull { sc.available(it + 1) && s.lines[it].formula == formula }
            if (dup != null) return bad(s, "That formula is already line ${dup + 1}. Try a different step.")
        }
        val cites = if (rule.isStandard) s.cites.sorted() else emptyList()
        val res = Proofs.checkLine(s.lines, rule, cites, formula, s.puzzle.conclusion)
        if (!res.ok) {
            val trap = res.trap ?: if (res.alt != null) Trap.ALT else Trap.OTHER
            return Outcome(
                s.copy(mistakes = s.mistakes + Mistake(formula.show(), rule, cites, res.message, trap), feedback = Feedback(FeedbackKind.BAD, res.message)),
                Effect.REJECT,
            )
        }
        val lines = s.lines + ProofLine(formula, rule, res.cites)
        val clash = Proofs.contradictions(lines)
        val innermostIsAip = Proofs.scopes(lines).open.lastOrNull()?.let { lines[it - 1].rule == Rule.AIP } == true
        val n = lines.size
        val contradictionNote = if (rule.isStandard && n in clash) {
            val other = clash[n]
            when {
                other == null && innermostIsAip -> "Contradiction! Line $n can't be true. Close the assumption with IP."
                other == null -> "Contradiction! Line $n can't be true, so something you assumed is impossible."
                innermostIsAip -> "Contradiction! Lines $other and $n say opposite things. Join them with Conj, then close the assumption with IP."
                else -> "Contradiction! Lines $other and $n say opposite things."
            }
        } else null
        val message = contradictionNote ?: when (rule.kind) {
            RuleKind.ASSUMPTION -> "Assumed $formula. Lines below are indented until you close it with ${if (rule == Rule.ACP) "CP" else "IP"}."
            RuleKind.CLOSE -> "Subproof closed: line ${lines.size} no longer depends on the assumption."
            else -> "Line ${lines.size} checks out: ${rule.title}."
        }
        val next = s.copy(
            lines = lines, tokens = emptyList(), caret = 0, rule = null, cites = emptyList(), hintStage = 0, hintTarget = null,
            feedback = Feedback(FeedbackKind.GOOD, message),
        )
        val solved = formula == s.puzzle.conclusion && Proofs.scopes(lines).open.isEmpty()
        return if (solved) Outcome(finish(next), Effect.SOLVED) else Outcome(next, Effect.ACCEPT)
    }

    fun undoLine(s: GameState): Outcome {
        if (s.lines.size <= s.premiseCount) return Outcome(s)
        val n = s.lines.size
        return Outcome(s.copy(lines = s.lines.dropLast(1), cites = s.cites.filter { it < n }, feedback = Feedback(FeedbackKind.INFO, "Removed line $n.")))
    }

    fun hint(s: GameState): Outcome {
        val h = Proofs.hint(s.puzzle, s.lines) ?: return Outcome(
            s.copy(feedback = Feedback(FeedbackKind.HINT, "Your proof has gone a different way from the model proof. Try Undo back to where the lines still help, then ask again.")),
            Effect.HINT,
        )
        var stage = if (s.hintTarget == h.step.formula) s.hintStage else 0
        var penalty = s.hintPenalty
        var hints = s.hints
        if (stage < 3) {
            stage++
            hints++
            penalty += HINT_COST[stage] - HINT_COST[stage - 1]
        }
        val r = h.step.rule
        val target: Formula = h.step.formula
        val where = if (h.cites.size == 1) "line ${h.cites[0]}" else "lines ${h.cites.joinToString(" and ")}"
        val fill = Fill(target.show().filter { !it.isWhitespace() }.toList(), r, if (r.isStandard) h.cites else emptyList())
        val fb = when (r.kind) {
            RuleKind.ASSUMPTION -> when (stage) {
                1 -> Feedback(FeedbackKind.HINT, if (r == Rule.ACP) "The conclusion is a conditional. Try a Conditional Proof: assume its \"if\" part." else "Try an Indirect Proof: assume the opposite of what you want, and look for a contradiction.")
                2 -> Feedback(FeedbackKind.HINT, "Choose ${r.abbr} (${r.title}) and assume something.")
                else -> Feedback(FeedbackKind.HINT, "Assume $target with ${r.abbr}.", fill = fill)
            }
            RuleKind.CLOSE -> when (stage) {
                1 -> Feedback(FeedbackKind.HINT, "You have what the subproof needs. Close it.")
                2 -> Feedback(FeedbackKind.HINT, "Close the assumption on line ${h.cites[0]} with ${r.abbr} (${r.title}).")
                else -> Feedback(FeedbackKind.HINT, "Write $target, justified by ${h.cites[0]}–${h.cites[1]}, ${r.abbr}.", fill = fill)
            }
            else -> when (stage) {
                1 -> Feedback(FeedbackKind.HINT, "Look at $where. What can they give you together?")
                2 -> Feedback(FeedbackKind.HINT, "Use ${r.title} (${r.abbr}) on $where.", "${r.from} ⟹ ${r.to}")
                else -> Feedback(FeedbackKind.HINT, "Write $target, justified by ${h.cites.joinToString(", ")}, ${r.abbr}.", fill = fill)
            }
        }
        return Outcome(s.copy(hintStage = stage, hintTarget = target, hints = hints, hintPenalty = penalty, feedback = fb), Effect.HINT)
    }

    fun giveUp(s: GameState) = finish(s.copy(revealed = true))

    /** Grades the proof: mistakes, hints and extra lines lower the score. */
    fun finish(state: GameState): GameState {
        val s = tick(state)
        val model = s.puzzle.solution.size
        val yours = s.lines.size - s.premiseCount
        val seconds = s.activeMs / 1000
        val result = if (s.revealed) GameResult(Grades.REVEALED, 0, 0, model, yours, seconds) else {
            val raw = 100 - 8 * s.mistakes.size - s.hintPenalty - 4 * maxOf(0, yours - model)
            val score = raw.coerceIn(50, 100)
            // S rank (daily proof only): no mistakes, no hints, and fewer lines than the model proof (beating par).
            val perfect = s.mode == Mode.DAILY && s.mistakes.isEmpty() && s.hints == 0 && yours < model
            val grade = when { perfect -> Grades.S; score >= 90 -> "A"; score >= 80 -> "B"; score >= 70 -> "C"; else -> "D" }
            GameResult(grade, when (grade) { Grades.S, "A" -> 3; "B" -> 2; else -> 1 }, score, model, yours, seconds)
        }
        return s.copy(done = true, result = result, lastTick = null)
    }
}
