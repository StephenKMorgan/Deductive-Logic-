package com.therefore.logic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therefore.logic.TheApp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.Attempt
import com.therefore.logic.data.Feedback
import com.therefore.logic.data.FeedbackKind
import com.therefore.logic.data.Fill
import com.therefore.logic.data.GameResult
import com.therefore.logic.data.GameState
import com.therefore.logic.data.Grades
import com.therefore.logic.data.Mistake
import com.therefore.logic.data.ProofLine
import com.therefore.logic.data.SavedGame
import com.therefore.logic.engine.Checker
import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.Generator
import com.therefore.logic.engine.ParseResult
import com.therefore.logic.engine.Parser
import com.therefore.logic.engine.Proofs
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.Trap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

/** Runs the puzzle being solved. Every change is saved, so a game survives the app closing. */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TheApp).container
    private val json = Json { ignoreUnknownKeys = true }
    private val saveLock = Mutex()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                c.db.savedGame().get()?.let { runCatching { json.decodeFromString<GameState>(it.json) }.getOrNull() }
            }
            _loaded.value = true
        }
    }

    private fun persist(s: GameState) {
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { c.db.savedGame().put(SavedGame(0, json.encodeToString(s))) }
        }
    }

    private inline fun update(f: (GameState) -> GameState) {
        val s = _state.value ?: return
        if (s.done) return
        val n = f(s)
        _state.value = n
        persist(n)
    }

    /** Clears a stale "line accepted" message once the next line is started. */
    private fun GameState.touched() =
        if (feedback?.kind == FeedbackKind.GOOD || feedback?.kind == FeedbackKind.INFO) copy(feedback = null) else this

    fun hasGameFor(courseId: String): Boolean {
        val s = _state.value ?: return false
        if (s.done || s.courseId != courseId) return false
        return !s.isDaily || s.date == LocalDate.now().toString()
    }

    suspend fun start(courseId: String) {
        val course = Curriculum.byId(courseId)
        val daily = courseId == Curriculum.DAILY_ID
        val today = LocalDate.now().toString()
        val level = if (daily) Curriculum.DAILY_LEVEL else c.progress.current().course(courseId).level
        val seed = if (daily) "therefore-daily-$today".hashCode().toLong() else Random.nextLong()
        val puzzle = withContext(Dispatchers.Default) { Generator.generate(course.spec, level, seed) }
        val s = GameState(
            courseId = courseId, level = level, date = if (daily) today else null, puzzle = puzzle,
            lines = puzzle.premises.map { ProofLine(it, null) }, startedAt = System.currentTimeMillis(),
        )
        _state.value = s
        persist(s)
    }

    // ----- Writing a line -----

    fun key(t: Char) = update {
        c.haptics.key()
        val tokens = it.tokens.toMutableList().apply { add(it.caret, t) }
        it.touched().copy(tokens = tokens, caret = it.caret + 1)
    }

    fun backspace() = update {
        if (it.caret == 0) return@update it
        c.haptics.key()
        val tokens = it.tokens.toMutableList().apply { removeAt(it.caret - 1) }
        it.touched().copy(tokens = tokens, caret = it.caret - 1)
    }

    fun moveCaret(delta: Int) = update { it.touched().copy(caret = (it.caret + delta).coerceIn(0, it.tokens.size)) }

    fun setCaret(i: Int) = update { it.touched().copy(caret = i.coerceIn(0, it.tokens.size)) }

    fun clearInput() = update { it.touched().copy(tokens = emptyList(), caret = 0) }

    fun toggleCite(n: Int) = update {
        c.haptics.key()
        val cites = if (n in it.cites) it.cites - n else it.cites + n
        it.touched().copy(cites = cites)
    }

    fun copyLine(n: Int) = update {
        val tokens = it.lines[n - 1].formula.show().filter { ch -> !ch.isWhitespace() }.toList()
        it.touched().copy(tokens = tokens, caret = tokens.size)
    }

    fun selectRule(r: Rule?) = update { it.touched().copy(rule = r) }

    fun fillHint() = update {
        val f = it.feedback?.fill ?: return@update it
        it.copy(tokens = f.tokens, caret = f.tokens.size, rule = f.rule, cites = f.cites)
    }

    fun addLine() {
        val s = _state.value ?: return
        if (s.done) return
        fun bad(text: String) = update { st -> st.copy(feedback = Feedback(FeedbackKind.BAD, text)) }
        val formula = when (val p = Parser.parse(s.tokens.joinToString(""))) {
            is ParseResult.Ok -> p.formula
            is ParseResult.Error -> { c.haptics.reject(); return bad(p.message) }
        }
        val rule = s.rule ?: run { c.haptics.reject(); return bad("Pick the rule that justifies this line.") }
        val dup = s.lines.indexOfFirst { it.formula == formula }
        if (dup >= 0) { c.haptics.reject(); return bad("That formula is already line ${dup + 1}. Try a different step.") }
        val cites = s.cites.sorted()
        val res = Checker.check(rule, cites.map { s.lines[it - 1].formula }, formula)
        if (!res.ok) {
            c.haptics.reject()
            val trap = res.trap ?: if (res.alt != null) Trap.ALT else Trap.OTHER
            update {
                it.copy(
                    mistakes = it.mistakes + Mistake(formula.show(), rule, cites, res.message, trap),
                    feedback = Feedback(FeedbackKind.BAD, res.message),
                )
            }
            return
        }
        val lines = s.lines + ProofLine(formula, rule, cites)
        val next = s.copy(
            lines = lines, tokens = emptyList(), caret = 0, rule = null, cites = emptyList(), hintStage = 0, hintTarget = null,
            feedback = Feedback(FeedbackKind.GOOD, "Line ${lines.size} checks out: ${rule.title}."),
        )
        if (formula == s.puzzle.conclusion) {
            c.haptics.solved()
            finish(next)
        } else {
            c.haptics.accept()
            _state.value = next
            persist(next)
        }
    }

    fun undoLine() = update {
        if (it.lines.size <= it.premiseCount) return@update it
        val n = it.lines.size
        it.copy(lines = it.lines.dropLast(1), cites = it.cites.filter { c -> c < n }, feedback = Feedback(FeedbackKind.INFO, "Removed line $n."))
    }

    // ----- Help -----

    fun hint() = update { s ->
        val h = Proofs.hint(s.puzzle, s.lines.map { it.formula }) ?: return@update s
        c.haptics.hint()
        var stage = if (s.hintTarget == h.step.formula) s.hintStage else 0
        var penalty = s.hintPenalty
        var hints = s.hints
        if (stage < 3) {
            stage++
            hints++
            penalty += HINT_COST[stage] - HINT_COST[stage - 1]
        }
        val where = if (h.cites.size == 1) "line ${h.cites[0]}" else "lines ${h.cites.joinToString(" and ")}"
        val r = h.step.rule
        val fb = when (stage) {
            1 -> Feedback(FeedbackKind.HINT, "Look at $where. What can they give you together?")
            2 -> Feedback(FeedbackKind.HINT, "Use ${r.title} (${r.abbr}) on $where.", "${r.from} ⟹ ${r.to}")
            else -> Feedback(
                FeedbackKind.HINT, "Write ${h.step.formula}, justified by ${h.cites.joinToString(", ")}, ${r.abbr}.",
                fill = Fill(h.step.formula.show().filter { !it.isWhitespace() }.toList(), r, h.cites),
            )
        }
        s.copy(hintStage = stage, hintTarget = h.step.formula, hints = hints, hintPenalty = penalty, feedback = fb)
    }

    fun giveUp() {
        val s = _state.value ?: return
        if (!s.done) finish(s.copy(revealed = true))
    }

    private fun finish(s: GameState) {
        val model = s.puzzle.solution.size
        val yours = s.lines.size - s.premiseCount
        val seconds = (System.currentTimeMillis() - s.startedAt) / 1000
        val result = if (s.revealed) GameResult(Grades.REVEALED, 0, 0, model, yours, seconds) else {
            val raw = 100 - 8 * s.mistakes.size - s.hintPenalty - 4 * maxOf(0, yours - model)
            val score = raw.coerceIn(50, 100)
            val grade = when { score >= 90 -> "A"; score >= 80 -> "B"; score >= 70 -> "C"; else -> "D" }
            GameResult(grade, when (grade) { "A" -> 3; "B" -> 2; else -> 1 }, score, model, yours, seconds)
        }
        val done = s.copy(done = true, result = result, feedback = null)
        _state.value = done
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { c.db.savedGame().put(SavedGame(0, json.encodeToString(done))) }
            c.db.attempts().insert(
                Attempt(
                    courseId = s.courseId, level = s.level, date = s.date ?: LocalDate.now().toString(),
                    grade = result.grade, stars = result.stars, score = result.score, lines = yours, modelLines = model,
                    mistakes = s.mistakes.size, hints = s.hints, seconds = seconds, revealed = s.revealed,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun resetAll() {
        _state.value = null
        viewModelScope.launch(Dispatchers.IO) {
            c.db.savedGame().clear()
            c.db.attempts().clear()
        }
    }

    companion object {
        private val HINT_COST = intArrayOf(0, 4, 8, 15)

        fun lineText(f: Formula) = f.show()
    }
}
