@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.therefore.logic.ui.screens

import com.therefore.logic.ext.satisfies
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.TableGoal
import com.therefore.logic.content.f
import com.therefore.logic.data.Progress
import com.therefore.logic.ui.LessonViewModel
import com.therefore.logic.ui.StepState
import com.therefore.logic.ui.components.CardShape
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.FormulaText
import com.therefore.logic.ui.components.Ledger
import com.therefore.logic.ui.components.LedgerRow
import com.therefore.logic.ui.components.LogicText
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.RuleCard
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LessonPlayerScreen(
    vm: LessonViewModel,
    progress: Progress,
    onClose: () -> Unit,
    onNextLesson: (String) -> Unit,
    onFreePlay: (level: Int) -> Unit,
    /** For courses practised with generated exercise sets: open a fresh set. */
    onPracticeSet: () -> Unit = {},
) {
    val p = LocalPalette.current
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val lesson = vm.lesson
    val (course, _) = Curriculum.lesson(lesson.id)
    var picking by rememberSaveable { mutableStateOf(false) }

    if (s.finished) {
        val next = course.lessons.getOrNull(course.lessons.indexOf(lesson) + 1)
        LessonComplete(
            lesson.title, next?.title, onClose, onNext = { next?.let { onNextLesson(it.id) } },
            onFreePlay = { if (course.practisesProofs) picking = true else onPracticeSet() }, onRestart = vm::restart,
            freePlayLabel = if (course.practisesProofs) "Free play this lesson" else "New practice set",
        )
        if (picking) LevelPicker("Free play: ${lesson.title}", progress.course(course.id).level, { picking = false }) { picking = false; onFreePlay(it) }
        return
    }

    val step = vm.currentStep
    val total = lesson.steps.size
    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close lesson") } },
                    title = {
                        Column {
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("Step ${s.stepIndex + 1} of $total · ${step.title}", style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
                )
                LinearProgressIndicator(
                    progress = { (s.stepIndex + if (s.step.complete) 1f else 0.5f) / total },
                    Modifier.fillMaxWidth(), color = p.accent, trackColor = p.sunk,
                )
            }
        },
        bottomBar = {
            if (step !is LessonStep.Guided && step !is LessonStep.Practice) StepNav(vm, s.stepIndex, s.step.complete, s.stepIndex == total - 1)
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (step) {
                is LessonStep.Read -> ReadStep(step)
                is LessonStep.Quiz -> QuizStep(vm, step, s.step)
                is LessonStep.Table -> TableStep(vm, step, s.step)
                is LessonStep.WorldStep -> WorldExplorer(vm, step, s.step)
                is LessonStep.SolverStep -> SolverLessonStep(vm, step, s.step)
                is LessonStep.TreeStep -> TreeLessonStep(vm, step, s.step)
                is LessonStep.OrderStep -> OrderLessonStep(vm, step, s.step)
                is LessonStep.KripkeStep -> KripkeLessonStep(vm, step, s.step)
                is LessonStep.TimelineStep -> TimelineLessonStep(vm, step, s.step)
                is LessonStep.Guided -> BoardStep(vm, s.step, s.stepIndex == total - 1, coachFor(step, s.step), step.success)
                is LessonStep.Practice -> BoardStep(vm, s.step, s.stepIndex == total - 1, step.prompt, "Proof complete. Nicely done.")
            }
        }
    }
}

private fun coachFor(step: LessonStep.Guided, st: StepState) = step.coach.getOrNull(st.coachIndex)?.text

@Composable
private fun StepNav(vm: LessonViewModel, index: Int, complete: Boolean, last: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(p.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (index > 0) OutlinedButton(onClick = vm::back) { Text("Back") }
        Button(onClick = vm::next, enabled = complete, modifier = Modifier.weight(1f)) { Text(if (last) "Finish lesson" else "Continue") }
    }
}

@Composable
private fun ReadStep(step: LessonStep.Read) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(step.title, style = MaterialTheme.typography.headlineSmall)
        step.body.forEach { LogicText(it, style = MaterialTheme.typography.bodyLarge) }
        step.example?.let { ex ->
            Panel {
                Ledger(
                    ex.lines.mapIndexed { i, (fs, j) -> LedgerRow(fs, j, premise = j.startsWith("Premise") || j.startsWith("Negated"), depth = ex.depths.getOrElse(i) { 0 }) },
                    ex.conclusion,
                )
            }
        }
        step.code?.let { com.therefore.logic.ui.components.CodeBox(it, lean = it.contains(":=")) }
        step.rules.forEach { RuleCard(it) }
        step.link?.let { (label, url) ->
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            OutlinedButton(onClick = { uri.openUri(url) }, Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrderLessonStep(vm: LessonViewModel, step: LessonStep.OrderStep, st: StepState) {
    val p = LocalPalette.current
    // A fixed shuffle per step, so the pieces don't jump around between taps.
    val pool = androidx.compose.runtime.remember(step) { (step.pieces + step.extra).shuffled(kotlin.random.Random(step.title.hashCode())) }
    val placed = step.pieces.take(st.placed)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Put it in order")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (placed.isEmpty()) Text("Tap the first piece below.", color = p.muted, style = MaterialTheme.typography.bodySmall)
                placed.forEachIndexed { i, piece ->
                    Row {
                        Text("${i + 1}. ", fontFamily = com.therefore.logic.ui.theme.Mono, color = p.muted, fontSize = 13.sp)
                        if (step.code) Text(piece, fontFamily = com.therefore.logic.ui.theme.Mono, fontSize = 13.sp)
                        else LogicText(piece, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (!st.complete) androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (piece in pool) if (piece !in placed) OutlinedButton(
                onClick = { vm.place(piece) },
                modifier = Modifier.semantics { contentDescription = "Piece: $piece" },
            ) {
                if (step.code) Text(piece, fontFamily = com.therefore.logic.ui.theme.Mono, fontSize = 13.sp) else LogicText(piece)
            }
        }
        st.wrong?.let { if (!st.complete) Panel(color = p.badSoft, border = false) { LogicText(it, color = p.ink) } }
        if (st.complete) Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
    }
}

@Composable
private fun QuizStep(vm: LessonViewModel, step: LessonStep.Quiz, st: StepState) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Quick check")
        LogicText(step.question, style = MaterialTheme.typography.titleLarge)
        step.options.forEachIndexed { i, opt ->
            val picked = st.picked == i
            val right = i == step.answer
            val bg by animateColorAsState(
                when {
                    picked && right -> p.goodSoft
                    picked -> p.badSoft
                    else -> p.surface
                }, label = "option",
            )
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(bg)
                    .border(1.5.dp, if (picked) (if (right) p.good else p.bad) else p.line, CardShape)
                    .clickable(enabled = st.picked == null) { vm.pick(i) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogicText(opt, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (picked) Text(if (right) "✓" else "✗", color = if (right) p.good else p.bad, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        when {
            st.picked == null -> {}
            st.complete -> Panel(color = p.goodSoft, border = false) {
                Column { Text("Correct", style = MaterialTheme.typography.titleSmall, color = p.good); LogicText(step.explain) }
            }
            else -> Panel(color = p.badSoft, border = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Not quite. Have another look.", Modifier.weight(1f), color = p.bad)
                    TextButton(onClick = vm::retryQuiz) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun TableStep(vm: LessonViewModel, step: LessonStep.Table, st: StepState) {
    val p = LocalPalette.current
    val formulas = step.formulas.map(::f)
    val rows = 1 shl st.values.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Explore")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        // The letter switches.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((letter, value) in st.values.toSortedMap()) {
                Column(
                    Modifier.weight(1f).clip(CardShape).background(if (value) p.accentSoft else p.sunk)
                        .border(1.5.dp, if (value) p.accent else p.line, CardShape)
                        .clickable { vm.toggle(letter) }
                        .semantics { contentDescription = "$letter is ${if (value) "true" else "false"}. Tap to flip." }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$letter", fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (value) "true" else "false", color = if (value) p.accent else p.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("Tap a letter to flip it between true and false.", style = MaterialTheme.typography.bodySmall, color = p.muted)
        // The formulas and their values on this row.
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                formulas.forEachIndexed { i, fm ->
                    val v = fm.eval(st.values)
                    val label = if (step.argument) (if (i == formulas.lastIndex) "Conclusion" else "Premise") else null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (label != null) Eyebrow(label)
                            FormulaText((if (label == "Conclusion") "∴ " else "") + fm.show(), style = FormulaStyle.copy(fontSize = 19.sp))
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (v) p.goodSoft else p.badSoft).padding(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text(if (v) "TRUE" else "FALSE", color = if (v) p.good else p.bad, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
        Text("Rows tried: ${st.visited.size} of $rows", style = MaterialTheme.typography.bodyMedium, color = p.muted)
        if (st.found) Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
        else if (step.goal == TableGoal.COUNTEREXAMPLE && st.visited.size > 1)
            Text("Keep going: you need every premise TRUE and the conclusion FALSE at the same time.", style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}

@Composable
private fun TreeLessonStep(vm: LessonViewModel, step: LessonStep.TreeStep, st: StepState) {
    val p = LocalPalette.current
    val t = st.tree ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Truth tree")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        FormulaText(step.premises.joinToString(",  ") + "   ∴ " + step.conclusion, style = FormulaStyle.copy(fontSize = 15.sp), color = p.muted)
        Text("The tree starts with the premises and the negated conclusion: can they all be true together?", style = MaterialTheme.typography.bodySmall, color = p.muted)
        Panel {
            com.therefore.logic.ui.components.TreeBoard(t, onSelect = { vm.treeSelect(if (t.selected == it) null else it) }, onClose = vm::treeClose)
        }
        val sel = t.selected
        if (sel != null && !t.finished) {
            val n = t.node(sel)
            Panel(color = p.accentSoft, border = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogicText("Line ${sel + 1}: ${n.f.show()}. Stack it or branch it?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.treeDecompose(sel, com.therefore.logic.ext.Shape.STACK) }, Modifier.weight(1f)) { Text("Stack") }
                        Button(onClick = { vm.treeDecompose(sel, com.therefore.logic.ext.Shape.BRANCH) }, Modifier.weight(1f)) { Text("Branch") }
                    }
                }
            }
        } else if (!t.finished) Text(
            "Tap a line to break it down. Tap Close under a branch once it has a formula and its negation.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        t.message?.let { Panel(color = if (t.messageBad) p.badSoft else p.sunk, border = false) { LogicText(it, color = p.ink) } }
        if (t.finished && t.verdict == null) {
            Text(
                if (t.allClosed) "Every branch is closed." else "One branch is open with nothing left to break down.",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("So is the argument valid?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.treeJudge(true) }, Modifier.weight(1f)) { Text("Valid") }
                OutlinedButton(onClick = { vm.treeJudge(false) }, Modifier.weight(1f)) { Text("Invalid") }
            }
        }
        if (st.complete) {
            val leaf = t.openComplete
            if (leaf != null) {
                val v = t.valuation(leaf).entries.joinToString(", ") { (l, b) -> "$l " + when (b) { true -> "true"; false -> "false"; null -> "either" } }
                Panel(color = p.sunk, border = false) { LogicText("The open branch gives the counterexample: $v.", color = p.ink) }
            }
            Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
        }
    }
}

@Composable
private fun KripkeLessonStep(vm: LessonViewModel, step: LessonStep.KripkeStep, st: StepState) {
    val p = LocalPalette.current
    val m = st.kripke ?: return
    val intuitionistic = step.logic == com.therefore.logic.content.KLogic.INTUITIONISTIC
    fun name(w: Int) = "w${w + 1}"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(if (intuitionistic) "States of knowledge" else "Possible worlds")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        com.therefore.logic.ui.components.WorldsDiagram(m, ::name)
        com.therefore.logic.ui.components.WorldControls(
            m, ::name, arrowsEditable = !step.fixedArrows, arrowWord = if (intuitionistic) "a later state" else "which worlds a world sees",
            onLetter = vm::toggleWorldLetter, onArrow = vm::toggleArrow,
        )
        Text(
            if (intuitionistic) "Arrows lead to later states, where more is known. A letter switched on stays on in every later state."
            else "□A: A is true in every world this one sees. ◇A: in at least one. Goals are about w1.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        com.therefore.logic.ui.components.TruthGrid(step.formulas, (0 until m.n).map(::name)) { f, w -> vm.worldValue(step, f, m, w) }
        val missing = step.frame.filter { !m.satisfies(it) }
        if (missing.isNotEmpty()) Text(
            "Only " + step.frame.joinToString(" and ") { it.label } + " models count here: " +
                missing.joinToString("; ") { when (it) {
                    com.therefore.logic.ext.FrameProp.REFLEXIVE -> "every world must see itself"
                    com.therefore.logic.ext.FrameProp.SYMMETRIC -> "every arrow needs one back"
                    com.therefore.logic.ext.FrameProp.TRANSITIVE -> "if a world sees one that sees another, it must see that one too"
                } } + ".",
            style = MaterialTheme.typography.bodySmall, color = p.hint,
        )
        if (step.impossible && !st.complete) OutlinedButton(onClick = vm::claimImpossible, enabled = st.tries >= 3, modifier = Modifier.fillMaxWidth()) {
            Text(if (st.tries >= 3) "It can't be done" else "Try a few models first")
        }
        if (st.complete) Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
    }
}

@Composable
private fun TimelineLessonStep(vm: LessonViewModel, step: LessonStep.TimelineStep, st: StepState) {
    val p = LocalPalette.current
    val trace = st.trace ?: return
    val letters = step.formulas.flatMap { com.therefore.logic.ext.XParser.of(it).atoms() }.distinct().sorted()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Timeline")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        com.therefore.logic.ui.components.TimelineControls(trace, letters, vm::toggleMoment)
        Text("□ always (from now on) · ◇ eventually · ○ at the next moment · A U B: B eventually, and A until then. Goals are about now.",
            style = MaterialTheme.typography.bodySmall, color = p.muted)
        com.therefore.logic.ui.components.TruthGrid(step.formulas, trace.indices.map { if (it == 0) "now" else "$it" }) { f, t -> vm.timelineValue(f, trace, t) }
        if (st.complete) Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
    }
}

@Composable
private fun SolverLessonStep(vm: LessonViewModel, step: LessonStep.SolverStep, st: StepState) {
    val p = LocalPalette.current
    val solver = st.solver ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(if (step.goal == com.therefore.logic.content.SolverGoal.REFUTE) "Prove it's unsatisfiable" else "Satisfy every clause")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        if (step.maxGuesses != null) Text(
            if (step.maxGuesses == 0) "Allowed guesses: none" else "Allowed guesses: ${step.maxGuesses}",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        com.therefore.logic.ui.components.SolverBoard(
            solver, step.propagateAll,
            onGuess = vm::solverGuess, onForce = vm::solverForce, onPropagate = vm::solverPropagate,
            onBacktrack = vm::solverBacktrack, onReset = vm::solverReset,
        )
        val tooMany = step.maxGuesses != null && solver.guesses > step.maxGuesses
        when {
            st.complete -> Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
            tooMany -> Panel(color = p.hintSoft, border = false) {
                Text(
                    if (step.maxGuesses == 0) "This one needs no guesses: everything can be forced. Press Start over and use only the \"forced\" tags."
                    else "That's ${solver.guesses} guesses; this one needs only ${step.maxGuesses}. Press Start over, guess once, then propagate before guessing again.",
                    color = p.ink,
                )
            }
        }
    }
}

/** Three things (a, b, c) and their properties; tap a square to give or take a property. */
@Composable
private fun WorldExplorer(vm: LessonViewModel, step: LessonStep.WorldStep, st: StepState) {
    val p = LocalPalette.current
    val world = vm.world(st)
    val formulas = step.formulas.map(::f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Explore a world")
        LogicText(step.prompt, style = MaterialTheme.typography.titleMedium)
        if (st.props.isNotEmpty()) Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(56.dp))
                    for (name in listOf("a", "b", "c")) Text(name, Modifier.weight(1f), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                for ((pred, things) in st.props.toSortedMap()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pred}x", Modifier.width(56.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        for (thing in 0..2) {
                            val on = thing in things
                            val name = "abc"[thing]
                            Box(
                                Modifier.weight(1f).padding(horizontal = 4.dp).height(52.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) p.accent else p.sunk)
                                    .border(1.5.dp, if (on) p.accent else p.line, RoundedCornerShape(10.dp))
                                    .clickable { vm.toggleProperty(pred, thing) }
                                    .semantics { contentDescription = "$name is ${if (on) "" else "not "}$pred. Tap to change." },
                                contentAlignment = Alignment.Center,
                            ) { Text(if (on) "$pred$name" else "¬$pred$name", fontFamily = Mono, color = if (on) p.onAccent else p.muted, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        // Relations: rows are the first thing, columns the second.
        for ((rel, pairs) in st.rels.toSortedMap()) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${rel}xy", Modifier.width(56.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        for (name in listOf("a", "b", "c")) Text(name, Modifier.weight(1f), fontFamily = Mono, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    for (i in 0..2) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${"abc"[i]}", Modifier.width(56.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        for (j in 0..2) {
                            val on = (i to j) in pairs
                            val label = "$rel${"abc"[i]}${"abc"[j]}"
                            Box(
                                Modifier.weight(1f).padding(3.dp).height(46.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) p.accent else p.sunk)
                                    .border(1.5.dp, if (on) p.accent else p.line, RoundedCornerShape(10.dp))
                                    .clickable { vm.toggleRelation(rel, i, j) }
                                    .semantics { contentDescription = "$label is ${if (on) "on" else "off"}. Tap to change." },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, fontFamily = Mono, color = if (on) p.onAccent else p.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
        Text(
            if (st.rels.isNotEmpty()) "Each square says whether the row thing relates to the column thing (Lab: a to b). Tap to switch it."
            else "Tap a square to give a thing that property, or take it away.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                formulas.forEachIndexed { i, fm ->
                    val v = fm.eval(world)
                    val label = if (step.argument) (if (i == formulas.lastIndex) "Conclusion" else "Premise") else null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (label != null) Eyebrow(label)
                            FormulaText((if (label == "Conclusion") "∴ " else "") + fm.show(), style = FormulaStyle.copy(fontSize = 19.sp))
                        }
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (v) p.goodSoft else p.badSoft).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(if (v) "TRUE" else "FALSE", color = if (v) p.good else p.bad, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (step.goal == com.therefore.logic.content.WorldGoal.EVERY_WORLD && !st.found)
            Text("Worlds tried: ${st.worldsSeen.size} of 6", style = MaterialTheme.typography.bodyMedium, color = p.muted)
        if (st.found) Panel(color = p.goodSoft, border = false) { LogicText(step.success, color = p.ink) }
    }
}

@Composable
private fun BoardStep(vm: LessonViewModel, st: StepState, last: Boolean, instruction: String?, success: String) {
    val p = LocalPalette.current
    val board = st.board ?: return
    Column(Modifier.fillMaxSize()) {
        // The coach.
        Column(
            Modifier.fillMaxWidth().background(if (st.complete) p.goodSoft else p.accentSoft).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedContent(st.complete to instruction, label = "coach") { (complete, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(if (complete) "✓" else "→", Modifier.width(22.dp), color = if (complete) p.good else p.accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    LogicText(if (complete) success else text.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (st.complete) {
                    Button(onClick = vm::next, Modifier.weight(1f)) { Text(if (last) "Finish lesson" else "Continue") }
                } else {
                    TextButton(onClick = vm::hint) { Text("Hint") }
                    TextButton(onClick = vm::resetBoard) { Text("Start over") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::back) { Text("Back") }
                }
            }
        }
        if (!st.complete) {
            ProofList(vm, board, Modifier.weight(1f))
            Composer(vm, board)
        } else {
            val sc = com.therefore.logic.engine.Proofs.scopes(board.lines)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Panel {
                    Ledger(
                        board.lines.mapIndexed { i, l -> LedgerRow(l.formula.show(), l.justification, premise = l.rule == null, depth = sc.depth(i + 1)) },
                        board.puzzle.conclusion.show(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonComplete(title: String, nextTitle: String?, onClose: () -> Unit, onNext: () -> Unit, onFreePlay: () -> Unit, onRestart: () -> Unit, freePlayLabel: String = "Free play this lesson") {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(p.goodSoft), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 44.sp, color = p.good, fontWeight = FontWeight.Bold)
        }
        Eyebrow("Lesson complete")
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("Practise what you learned with free play, or keep going.", style = MaterialTheme.typography.bodyLarge, color = p.muted)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onFreePlay, Modifier.fillMaxWidth()) { Text(freePlayLabel) }
        if (nextTitle != null) OutlinedButton(onClick = onNext, Modifier.fillMaxWidth()) { Text("Next lesson: $nextTitle") }
        OutlinedButton(onClick = onClose, Modifier.fillMaxWidth()) { Text("Back to the course") }
        TextButton(onClick = onRestart) { Text("Go through it again") }
    }
}
