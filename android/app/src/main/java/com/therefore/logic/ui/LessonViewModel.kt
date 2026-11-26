package com.therefore.logic.ui

import com.therefore.logic.ext.satisfies
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therefore.logic.TheApp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonDef
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.KLogic
import com.therefore.logic.content.SolverGoal
import com.therefore.logic.content.WorldsGoal
import com.therefore.logic.content.TableGoal
import com.therefore.logic.content.WorldGoal
import com.therefore.logic.engine.World
import com.therefore.logic.engine.arities
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
    /** World explorer: which things (0, 1, 2) have each property, and the worlds tried so far. */
    val props: Map<Char, Set<Int>> = emptyMap(),
    /** Two-place relations: the ordered pairs (row thing, column thing) that are on. */
    val rels: Map<Char, Set<Pair<Int, Int>>> = emptyMap(),
    val worldsSeen: Set<String> = emptySet(),
    /** Put-in-order steps: how many pieces are placed, and the last wrong tap. */
    val placed: Int = 0,
    val wrong: String? = null,
    /** A truth tree. */
    val tree: com.therefore.logic.ext.TreeState? = null,
    /** A possible-worlds model, and how many changes have been tried (for "it can't be done" steps). */
    val kripke: com.therefore.logic.ext.Kripke? = null,
    val tries: Int = 0,
    /** A timeline: the letters true at each moment. */
    val trace: List<Set<Char>>? = null,
    /** The solver board (DPLL by hand). */
    val solver: com.therefore.logic.games.SolverState? = null,
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
        is LessonStep.WorldStep -> {
            val arity = sortedMapOf<Char, Int>().also { m -> step.formulas.forEach { f(it).arities(m) } }
            val props = arity.filterValues { it == 1 }.keys.associateWith { emptySet<Int>() }
            val rels = arity.filterValues { it == 2 }.keys.associateWith { emptySet<Pair<Int, Int>>() }
            evaluateWorld(step, StepState(props = props, rels = rels, worldsSeen = setOf(worldKey(props, rels))))
        }
        is LessonStep.OrderStep -> StepState()
        is LessonStep.TreeStep -> StepState(tree = com.therefore.logic.ext.Trees.start(step.premises.map(::f), f(step.conclusion)))
        is LessonStep.KripkeStep -> {
            val letters = step.formulas.flatMap { com.therefore.logic.ext.XParser.of(it).atoms() }.toSortedSet()
            evaluateKripke(step, StepState(kripke = com.therefore.logic.ext.Kripke(step.worlds, step.arrows, letters.associateWith { emptySet() })))
        }
        is LessonStep.TimelineStep -> {
            val trace = List(step.moments) { t -> step.start.getOrElse(t) { "" }.toSet() }
            evaluateTimeline(step, StepState(trace = trace))
        }
        is LessonStep.SolverStep -> StepState(solver = com.therefore.logic.games.SolverState(com.therefore.logic.games.Cnf.parse(step.clauses)))
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
            TableGoal.ALL_TRUE -> fs.all { it.eval(st.values) }
        }
        val found = st.found || hit
        return st.copy(found = found, complete = found)
    }

    private fun worldKey(props: Map<Char, Set<Int>>, rels: Map<Char, Set<Pair<Int, Int>>>) =
        props.toSortedMap().entries.joinToString("|") { (k, v) -> "$k${v.sorted()}" } + "#" +
            rels.toSortedMap().entries.joinToString("|") { (k, v) -> "$k${v.sortedBy { it.first * 3 + it.second }}" }

    fun world(st: StepState) = World(3, st.props, mapOf('a' to 0, 'b' to 1, 'c' to 2), rels = st.rels)

    private fun evaluateWorld(step: LessonStep.WorldStep, st: StepState): StepState {
        val w = world(st)
        val v = step.formulas.map { f(it).eval(w) }
        val worlds = 1 shl minOf(20, 3 * st.props.size + 9 * st.rels.size)
        val hit = when (step.goal) {
            WorldGoal.ALL_TRUE -> v.all { it }
            WorldGoal.FALSE -> !v[0]
            WorldGoal.COUNTEREXAMPLE -> v.dropLast(1).all { it } && !v.last()
            WorldGoal.EVERY_WORLD -> st.worldsSeen.size >= minOf(worlds, 6)
        }
        return st.copy(found = st.found || hit, complete = st.found || hit)
    }

    /** Gives or takes property [p] from thing [thing] in a world explorer. */
    fun toggleProperty(p: Char, thing: Int) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.WorldStep ?: return
        val cur = s.step.props[p] ?: emptySet()
        val props = s.step.props + (p to if (thing in cur) cur - thing else cur + thing)
        c.haptics.key()
        val st = evaluateWorld(step, s.step.copy(props = props, worldsSeen = s.step.worldsSeen + worldKey(props, s.step.rels)))
        if (st.found && !s.step.found) c.haptics.solved()
        _state.value = s.copy(step = st)
    }

    /** Turns relation [r] from thing [i] to thing [j] on or off. */
    fun toggleRelation(r: Char, i: Int, j: Int) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.WorldStep ?: return
        val cur = s.step.rels[r] ?: emptySet()
        val rels = s.step.rels + (r to if ((i to j) in cur) cur - (i to j) else cur + (i to j))
        c.haptics.key()
        val st = evaluateWorld(step, s.step.copy(rels = rels, worldsSeen = s.step.worldsSeen + worldKey(s.step.props, rels)))
        if (st.found && !s.step.found) c.haptics.solved()
        _state.value = s.copy(step = st)
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

    // ----- Put in order -----

    fun place(piece: String) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.OrderStep ?: return
        if (s.step.complete) return
        val next = step.pieces.getOrNull(s.step.placed) ?: return
        if (piece == next) {
            val placed = s.step.placed + 1
            val done = placed == step.pieces.size
            if (done) c.haptics.solved() else c.haptics.accept()
            _state.value = s.copy(step = s.step.copy(placed = placed, wrong = null, complete = done))
        } else {
            c.haptics.reject()
            _state.value = s.copy(step = s.step.copy(wrong = if (piece in step.extra) "That piece doesn't belong in this one at all." else "Not next. " + step.why))
        }
    }

    // ----- Truth trees -----

    private fun tree(f: (com.therefore.logic.ext.TreeState) -> com.therefore.logic.ext.TreeState) {
        val s = _state.value ?: return
        val before = s.step.tree ?: return
        if (s.step.complete) return
        val after = f(before)
        if (after == before) return
        val done = after.verdict != null
        when {
            done -> c.haptics.solved()
            after.mistakes > before.mistakes -> c.haptics.reject()
            after.closed.size > before.closed.size -> c.haptics.accept()
            else -> c.haptics.key()
        }
        _state.value = s.copy(step = s.step.copy(tree = after, complete = done))
    }

    fun treeSelect(id: Int?) = tree { com.therefore.logic.ext.Trees.select(it, id) }
    fun treeDecompose(id: Int, shape: com.therefore.logic.ext.Shape) = tree { com.therefore.logic.ext.Trees.decompose(it, id, shape) }
    fun treeClose(leaf: Int) = tree { com.therefore.logic.ext.Trees.close(it, leaf) }
    fun treeJudge(valid: Boolean) = tree { com.therefore.logic.ext.Trees.judge(it, valid) }

    // ----- Possible worlds -----

    private fun kripkeMet(step: LessonStep.KripkeStep, m: com.therefore.logic.ext.Kripke): Boolean {
        if (step.impossible) return false
        if (!step.frame.all { m.satisfies(it) }) return false
        val v = step.formulas.map { worldValue(step, it, m, 0) }
        return when (step.goal) {
            WorldsGoal.ALL_TRUE -> v.all { it }
            WorldsGoal.FALSE -> !v[0]
        }
    }

    fun worldValue(step: LessonStep.KripkeStep, formula: String, m: com.therefore.logic.ext.Kripke, w: Int): Boolean {
        val x = com.therefore.logic.ext.XParser.of(formula)
        return if (step.logic == KLogic.MODAL) com.therefore.logic.ext.Semantics.modal(x, m, w) else com.therefore.logic.ext.Semantics.intuitionistic(x, m, w)
    }

    private fun evaluateKripke(step: LessonStep.KripkeStep, st: StepState): StepState {
        val met = kripkeMet(step, st.kripke!!)
        return st.copy(found = st.found || met, complete = st.complete || met)
    }

    private fun kripke(change: (com.therefore.logic.ext.Kripke) -> com.therefore.logic.ext.Kripke) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.KripkeStep ?: return
        val m = s.step.kripke ?: return
        val st = evaluateKripke(step, s.step.copy(kripke = change(m), tries = s.step.tries + 1))
        if (st.complete && !s.step.complete) c.haptics.solved() else c.haptics.key()
        _state.value = s.copy(step = st)
    }

    /** Switches letter [p] at world [w]. In intuitionistic logic what's established stays established, so later worlds follow along. */
    fun toggleWorldLetter(p: Char, w: Int) = kripke { m ->
        val step = currentStep as LessonStep.KripkeStep
        val cur = m.truth[p] ?: emptySet()
        val on = w !in cur
        val next = when {
            step.logic == KLogic.INTUITIONISTIC && on -> cur + m.later(w)
            step.logic == KLogic.INTUITIONISTIC -> cur - (0 until m.n).filter { w in m.later(it) }.toSet()
            on -> cur + w
            else -> cur - w
        }
        m.copy(truth = m.truth + (p to next))
    }

    fun toggleArrow(i: Int, j: Int) = kripke { m ->
        val step = currentStep as LessonStep.KripkeStep
        if (step.fixedArrows) m else m.copy(arrows = if ((i to j) in m.arrows) m.arrows - (i to j) else m.arrows + (i to j))
    }

    /** For an impossible goal: the learner says it can't be done, after trying a few models. */
    fun claimImpossible() {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.KripkeStep ?: return
        if (!step.impossible || s.step.tries < 3) return
        c.haptics.solved()
        _state.value = s.copy(step = s.step.copy(complete = true, found = true))
    }

    // ----- Timelines -----

    fun timelineValue(formula: String, trace: List<Set<Char>>, t: Int) =
        com.therefore.logic.ext.Semantics.temporal(com.therefore.logic.ext.XParser.of(formula), trace, t)

    private fun evaluateTimeline(step: LessonStep.TimelineStep, st: StepState): StepState {
        val v = step.formulas.map { timelineValue(it, st.trace!!, 0) }
        val met = when (step.goal) { WorldsGoal.ALL_TRUE -> v.all { it }; WorldsGoal.FALSE -> !v[0] }
        return st.copy(found = st.found || met, complete = st.complete || met)
    }

    fun toggleMoment(p: Char, t: Int) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.TimelineStep ?: return
        val trace = s.step.trace ?: return
        val next = trace.mapIndexed { i, set -> if (i != t) set else if (p in set) set - p else set + p }
        val st = evaluateTimeline(step, s.step.copy(trace = next))
        if (st.complete && !s.step.complete) c.haptics.solved() else c.haptics.key()
        _state.value = s.copy(step = st)
    }

    // ----- The solver board -----

    private fun solver(f: (com.therefore.logic.games.SolverState) -> com.therefore.logic.games.SolverState) {
        val s = _state.value ?: return
        val step = currentStep as? LessonStep.SolverStep ?: return
        val before = s.step.solver ?: return
        if (s.step.complete) return
        val after = f(before)
        if (after == before) return
        val met = when (step.goal) {
            SolverGoal.SATISFY -> after.solved && (step.maxGuesses == null || after.guesses <= step.maxGuesses)
            SolverGoal.REFUTE -> after.unsat
        }
        when {
            met -> c.haptics.solved()
            after.conflict != null && before.conflict == null -> c.haptics.reject()
            else -> c.haptics.key()
        }
        _state.value = s.copy(step = s.step.copy(solver = after, complete = met))
    }

    fun solverGuess(v: Int, value: Boolean) = solver { com.therefore.logic.games.Solver.guess(it, v, value) }
    fun solverForce(i: Int) = solver { com.therefore.logic.games.Solver.force(it, i) }
    fun solverPropagate() = solver { com.therefore.logic.games.Solver.propagate(it) }
    fun solverBacktrack() = solver { com.therefore.logic.games.Solver.backtrack(it) }
    fun solverReset() {
        val s = _state.value ?: return
        if (s.step.complete) return
        _state.value = s.copy(step = initial(currentStep))
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
            // Practice sets are generated fresh each time, so they don't count as finished lessons.
            if (!Curriculum.isPracticeSet(lesson.id))
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
