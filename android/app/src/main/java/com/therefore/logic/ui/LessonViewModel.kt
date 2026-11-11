package com.therefore.logic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therefore.logic.TheApp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonDef
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.TableGoal
import com.therefore.logic.content.f
import com.therefore.logic.content.puzzle
import com.therefore.logic.data.GameState
import com.therefore.logic.data.LessonDone
import com.therefore.logic.data.Mode
import com.therefore.logic.engine.Rule
import com.therefore.logic.game.BoardActions
import com.therefore.logic.game.Outcome
import com.therefore.logic.game.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Progress through one step of a lesson. */
data class StepState(
    /** Quiz: the option tapped, if any. */
    val picked: Int? = null,
    /** Table: the current truth values, and every row visited so far (as bit masks). */
    val values: Map<Char, Boolean> = emptyMap(),
    val visited: Set<Int> = emptySet(),
    val found: Boolean = false,
    /** Guided / practice proofs. */
    val board: GameState? = null,
    val coachIndex: Int = 0,
    val complete: Boolean = false,
)

data class LessonState(val lessonId: String, val stepIndex: Int = 0, val step: StepState = StepState(), val finished: Boolean = false)

/** Walks through a lesson's steps: reading, quizzes, truth-table explorers and coached proofs. */
class LessonViewModel(app: Application) : AndroidViewModel(app), BoardActions {
    private val c = (app as TheApp).container
    private val _state = MutableStateFlow<LessonState?>(null)
    val state: StateFlow<LessonState?> = _state.asStateFlow()

    lateinit var lesson: LessonDef
        private set
    private lateinit var courseId: String

    fun open(lessonId: String) {
        if (_state.value?.lessonId == lessonId) return
        val (course, l) = Curriculum.lesson(lessonId)
        lesson = l
        courseId = course.id
        _state.value = LessonState(lessonId, 0, initial(l.steps[0]))
    }

    val currentStep: LessonStep get() = lesson.steps[_state.value!!.stepIndex]

    private fun initial(step: LessonStep): StepState = when (step) {
        is LessonStep.Read -> StepState(complete = true)
        is LessonStep.Quiz -> StepState()
        is LessonStep.Table -> {
            val letters = step.formulas.flatMap { f(it).atoms() }.distinct().sorted()
            val values = letters.associateWith { true }
            StepState(values = values, visited = setOf(mask(values))).let { evaluateTable(step, it) }
        }
        is LessonStep.Guided -> StepState(board = Session.newGame(courseId, 1, Mode.LESSON, step.puzzle(), Curriculum.boardRules(step.rules)))
        is LessonStep.Practice -> StepState(board = Session.newGame(courseId, 1, Mode.LESSON, step.puzzle(), Curriculum.boardRules(step.rules)))
    }

    private fun mask(v: Map<Char, Boolean>) = v.keys.sorted().foldIndexed(0) { i, m, k -> if (v.getValue(k)) m or (1 shl i) else m }

    private fun evaluateTable(step: LessonStep.Table, st: StepState): StepState {
        val fs = step.formulas.map(::f)
        val rows = 1 shl st.values.size
        val hit = when (step.goal) {
            TableGoal.FALSE_ROW -> !fs[0].eval(st.values)
            TableGoal.COUNTEREXAMPLE -> fs.dropLast(1).all { it.eval(st.values) } && !fs.last().eval(st.values)
            TableGoal.ALL_ROWS -> st.visited.size == rows
        }
        val found = st.found || hit
        return st.copy(found = found, complete = found)
    }

    fun toggle(letter: Char) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.Table ?: return
        val values = s.step.values + (letter to !s.step.values.getValue(letter))
        c.haptics.key()
        val st = evaluateTable(step, s.step.copy(values = values, visited = s.step.visited + mask(values)))
        if (st.found && !s.step.found) c.haptics.solved()
        _state.value = s.copy(step = st)
    }

    fun pick(option: Int) {
        val s = _state.value ?: return
        val quiz = currentStep as? LessonStep.Quiz ?: return
        if (s.step.complete) return
        val right = option == quiz.answer
        if (right) c.haptics.accept() else c.haptics.reject()
        _state.value = s.copy(step = s.step.copy(picked = option, complete = right))
    }

    fun retryQuiz() {
        val s = _state.value ?: return
        _state.value = s.copy(step = s.step.copy(picked = null))
    }

    fun next() {
        val s = _state.value ?: return
        if (!s.step.complete) return
        val i = s.stepIndex + 1
        if (i >= lesson.steps.size) {
            _state.value = s.copy(finished = true)
            viewModelScope.launch(Dispatchers.IO) { c.db.lessons().markDone(LessonDone(lesson.id, System.currentTimeMillis())) }
        } else {
            _state.value = s.copy(stepIndex = i, step = initial(lesson.steps[i]))
        }
    }

    fun back() {
        val s = _state.value ?: return
        if (s.stepIndex == 0) return
        _state.value = s.copy(stepIndex = s.stepIndex - 1, step = initial(lesson.steps[s.stepIndex - 1]))
    }

    fun restart() {
        val s = _state.value ?: return
        _state.value = s.copy(stepIndex = 0, step = initial(lesson.steps[0]), finished = false)
    }

    // ----- The proof board inside guided and practice steps -----

    private fun board(f: (GameState) -> Outcome) {
        val s = _state.value ?: return
        val b = s.step.board ?: return
        if (b.done) return
        val o = f(b)
        c.haptics.play(o.effect)
        var st = s.step.copy(board = o.state)
        val step = currentStep
        if (step is LessonStep.Guided) {
            // Advance the coach through every instruction that is now satisfied.
            var i = st.coachIndex
            while (i < step.coach.size && step.coach[i].done(o.state)) i++
            st = st.copy(coachIndex = i, complete = i >= step.coach.size)
        } else if (step is LessonStep.Practice) {
            st = st.copy(complete = o.state.done)
        }
        _state.value = s.copy(step = st)
    }

    override fun key(t: Char) = board { Session.key(it, t) }
    override fun backspace() = board { Session.backspace(it) }
    override fun moveCaret(delta: Int) = board { Session.setCaret(it, it.caret + delta) }
    override fun setCaret(i: Int) = board { Session.setCaret(it, i) }
    override fun toggleCite(n: Int) = board { Session.toggleCite(it, n) }
    override fun copyLine(n: Int) = board { Session.copyLine(it, n) }
    override fun selectRule(r: Rule?) = board { Session.selectRule(it, r) }
    override fun fillHint() = board { Session.fillHint(it) }
    override fun addLine() = board { Session.addLine(it) }
    override fun undoLine() = board { Session.undoLine(it) }
    override fun hint() = board { Session.hint(it) }

    fun resetBoard() {
        val s = _state.value ?: return
        _state.value = s.copy(step = initial(currentStep))
    }
}
