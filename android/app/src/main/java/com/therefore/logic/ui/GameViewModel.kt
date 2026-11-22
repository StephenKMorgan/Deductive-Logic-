package com.therefore.logic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therefore.logic.TheApp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.Attempt
import com.therefore.logic.data.GameState
import com.therefore.logic.data.Mode
import com.therefore.logic.data.SavedGame
import com.therefore.logic.engine.Generator
import com.therefore.logic.engine.Rule
import com.therefore.logic.game.BoardActions
import com.therefore.logic.game.Effect
import com.therefore.logic.game.Outcome
import com.therefore.logic.game.Session
import com.therefore.logic.haptics.Haptics
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

fun Haptics.play(effect: Effect?) = when (effect) {
    Effect.KEY -> key()
    Effect.ACCEPT -> accept()
    Effect.REJECT -> reject()
    Effect.HINT -> hint()
    Effect.SOLVED -> solved()
    null -> Unit
}

/** Runs the puzzle being solved (practice, free play or daily). Every change is saved. */
class GameViewModel(app: Application) : AndroidViewModel(app), BoardActions {
    private val c = (app as TheApp).container
    private val json = Json { ignoreUnknownKeys = true }
    private val saveLock = Mutex()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                c.db.savedGame().get()?.let { runCatching { json.decodeFromString<GameState>(it.json) }.getOrNull() }
            }
            // Don't overwrite a game that was started while loading.
            if (_state.value == null) _state.value = saved
            _loaded.value = true
        }
    }

    private fun persist(s: GameState) {
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { c.db.savedGame().put(SavedGame(0, json.encodeToString(s))) }
        }
    }

    private fun apply(f: (GameState) -> Outcome) {
        val s = _state.value ?: return
        if (s.done) return
        val o = f(s)
        c.haptics.play(o.effect)
        _state.value = o.state
        persist(o.state)
        if (o.state.done) record(o.state)
    }

    /** Whether there's an unfinished puzzle of this kind to resume. */
    fun hasGame(courseId: String, mode: Mode, lessonId: String? = null, level: Int? = null, date: LocalDate = LocalDate.now()): Boolean {
        val s = _state.value ?: return false
        if (s.done || s.courseId != courseId || s.mode != mode || s.lessonId != lessonId) return false
        if (level != null && s.level != level) return false
        return mode != Mode.DAILY || s.date == date.toString()
    }

    /** Generates a new puzzle. Practice uses the course level; free play uses the chosen level. */
    /** Generates a new puzzle. [date] picks which day's daily proof (past days can be caught up on). */
    suspend fun start(courseId: String, mode: Mode, level: Int? = null, lessonId: String? = null, date: LocalDate = LocalDate.now()) {
        val course = Curriculum.byId(courseId)
        val today = date.toString()
        val lesson = lessonId?.let { id -> course.lessons.firstOrNull { it.id == id } }
        val lvl = when (mode) {
            Mode.DAILY -> Curriculum.DAILY_LEVEL
            Mode.FREE -> level ?: 1
            else -> c.progress.current().course(courseId).level
        }
        val seed = when {
            mode != Mode.DAILY -> Random.nextLong()
            courseId == Curriculum.DAILY_ID -> "therefore-daily-$today".hashCode().toLong()
            else -> "therefore-$courseId-$today".hashCode().toLong()
        }
        val spec = course.specFor(lesson)
        val puzzle = withContext(Dispatchers.Default) { Generator.generate(spec, lvl, seed) }
        val s = Session.newGame(courseId, lvl, mode, puzzle, Curriculum.boardRules(course.spec.rules), if (mode == Mode.DAILY) today else null, lessonId)
        _state.value = s
        persist(s)
    }

    private fun record(s: GameState) {
        val r = s.result ?: return
        viewModelScope.launch(Dispatchers.IO) {
            c.db.attempts().insert(
                Attempt(
                    courseId = s.courseId, level = s.level, date = s.date ?: LocalDate.now().toString(),
                    grade = r.grade, stars = r.stars, score = r.score, lines = r.yourLines, modelLines = r.modelLines,
                    mistakes = s.mistakes.size, hints = s.hints, seconds = r.seconds, revealed = s.revealed,
                    finishedAt = System.currentTimeMillis(), mode = s.mode.name,
                    rulesUsed = s.lines.mapNotNull { it.rule?.abbr }.joinToString(","),
                    mistakeRules = s.mistakes.joinToString(",") { it.rule.abbr },
                ),
            )
        }
    }

    override fun key(t: Char) = apply { Session.key(it, t) }
    override fun backspace() = apply { Session.backspace(it) }
    override fun moveCaret(delta: Int) = apply { Session.setCaret(it, it.caret + delta) }
    override fun setCaret(i: Int) = apply { Session.setCaret(it, i) }
    override fun toggleCite(n: Int) = apply { Session.toggleCite(it, n) }
    override fun copyLine(n: Int) = apply { Session.copyLine(it, n) }
    override fun selectRule(r: Rule?) = apply { Session.selectRule(it, r) }
    override fun fillHint() = apply { Session.fillHint(it) }
    override fun addLine() = apply { Session.addLine(it) }
    override fun undoLine() = apply { Session.undoLine(it) }
    override fun hint() = apply { Session.hint(it) }
    fun giveUp() = apply { Outcome(Session.giveUp(it)) }

    fun resetAll() {
        _state.value = null
        viewModelScope.launch(Dispatchers.IO) {
            c.db.savedGame().clear()
            c.db.attempts().clear()
            c.db.lessons().clear()
        }
    }
}
