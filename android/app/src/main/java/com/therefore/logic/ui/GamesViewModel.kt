package com.therefore.logic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therefore.logic.TheApp
import com.therefore.logic.games.Board
import com.therefore.logic.games.IslandPuzzle
import com.therefore.logic.games.Question
import com.therefore.logic.games.Switchboard
import com.therefore.logic.games.TruthTellers
import com.therefore.logic.games.ValidOrNot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class IslandState(val puzzle: IslandPuzzle, val answers: List<Boolean?>, val checked: Boolean = false, val mistake: String? = null, val correct: Boolean = false)

data class QuizState(
    val question: Question? = null,
    val score: Int = 0,
    val streak: Int = 0,
    val timeLeft: Int = ROUND_SECONDS,
    val playing: Boolean = false,
    val over: Boolean = false,
    /** The last answer: right or wrong, with its explanation. */
    val lastRight: Boolean? = null,
    val lastExplanation: String? = null,
    val missed: List<Question> = emptyList(),
    val asked: Int = 0,
) { companion object { const val ROUND_SECONDS = 60 } }

data class BoardState(val board: Board, val switches: Map<Char, Boolean>, val moves: Int = 0, val solved: Boolean = false) {
    val satisfied get() = board.constraints.map { it.eval(switches) }
    val minMoves get() = board.solution.count { it.value }
}

/** Runs the three games in the Games tab. */
class GamesViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TheApp).container
    private val rng = Random(System.nanoTime())

    val stats = c.games.stats

    private val _island = MutableStateFlow<IslandState?>(null)
    val island: StateFlow<IslandState?> = _island.asStateFlow()

    private val _quiz = MutableStateFlow(QuizState())
    val quiz: StateFlow<QuizState> = _quiz.asStateFlow()

    private val _board = MutableStateFlow<BoardState?>(null)
    val board: StateFlow<BoardState?> = _board.asStateFlow()

    // ----- Truth-tellers & Liars -----

    fun newIsland() = viewModelScope.launch {
        val streak = stats.first().islandStreak
        val people = when { streak < 3 -> 2; streak < 8 -> 3; else -> 4 }
        val p = withContext(Dispatchers.Default) { TruthTellers.generate(people, rng) }
        _island.value = IslandState(p, List(p.names.size) { null })
    }

    fun answer(i: Int, truthTeller: Boolean) {
        val s = _island.value ?: return
        if (s.correct) return
        c.haptics.key()
        _island.value = s.copy(answers = s.answers.toMutableList().apply { this[i] = truthTeller }, checked = false, mistake = null)
    }

    fun checkIsland() {
        val s = _island.value ?: return
        if (s.answers.any { it == null } || s.correct) return
        val answer = s.answers.map { it!! }
        val right = answer == s.puzzle.solution
        if (right) c.haptics.solved() else c.haptics.reject()
        _island.value = s.copy(checked = true, correct = right, mistake = if (right) null else TruthTellers.mistake(s.puzzle, answer))
        viewModelScope.launch { c.games.island(right) }
    }

    // ----- Valid or Not? -----

    private var timer: Job? = null

    fun startQuiz() {
        timer?.cancel()
        _quiz.value = QuizState(question = ValidOrNot.next(rng, 0), playing = true)
        timer = viewModelScope.launch {
            while (_quiz.value.timeLeft > 0 && _quiz.value.playing) {
                delay(1000)
                _quiz.value = _quiz.value.let { it.copy(timeLeft = (it.timeLeft - 1).coerceAtLeast(0)) }
            }
            endQuiz()
        }
    }

    fun answerQuiz(yes: Boolean) {
        val s = _quiz.value
        val q = s.question ?: return
        if (!s.playing) return
        val right = yes == q.answer
        if (right) c.haptics.accept() else c.haptics.reject()
        val asked = s.asked + 1
        _quiz.value = s.copy(
            score = s.score + if (right) 1 + (s.streak / 5) else 0,   // a bonus point for every 5 in a row
            streak = if (right) s.streak + 1 else 0,
            timeLeft = if (right) s.timeLeft else (s.timeLeft - 3).coerceAtLeast(0),
            lastRight = right, lastExplanation = q.explanation,
            missed = if (right) s.missed else s.missed + q,
            question = ValidOrNot.next(rng, asked), asked = asked,
        )
        if (_quiz.value.timeLeft == 0) endQuiz()
    }

    private fun endQuiz() {
        val s = _quiz.value
        if (!s.playing) return
        timer?.cancel()
        _quiz.value = s.copy(playing = false, over = true)
        viewModelScope.launch { c.games.quiz(s.score) }
    }

    fun leaveQuiz() {
        timer?.cancel()
        _quiz.value = QuizState()
    }

    // ----- Switchboard -----

    fun newBoard() = viewModelScope.launch {
        val solved = stats.first().boardSolved
        val n = minOf(6, 3 + solved / 3)
        val b = withContext(Dispatchers.Default) { Switchboard.generate(n, rng) }
        _board.value = BoardState(b, b.letters.associateWith { false })
    }

    fun flip(l: Char) {
        val s = _board.value ?: return
        if (s.solved) return
        c.haptics.key()
        val switches = s.switches + (l to !s.switches.getValue(l))
        val solved = s.board.constraints.all { it.eval(switches) }
        _board.value = s.copy(switches = switches, moves = s.moves + 1, solved = solved)
        if (solved) {
            c.haptics.solved()
            viewModelScope.launch { c.games.board(perfect = s.moves + 1 <= s.minMoves) }
        }
    }
}
