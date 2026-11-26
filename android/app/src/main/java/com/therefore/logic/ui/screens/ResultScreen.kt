@file:OptIn(ExperimentalMaterial3Api::class)

package com.therefore.logic.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.GameState
import com.therefore.logic.data.Grades
import com.therefore.logic.engine.Proofs
import com.therefore.logic.engine.Trap
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.FormulaText
import com.therefore.logic.ui.components.Ledger
import com.therefore.logic.ui.components.LedgerRow
import com.therefore.logic.ui.components.LogicText
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.RuleCard
import com.therefore.logic.ui.components.SectionTitle
import com.therefore.logic.ui.components.Tag
import com.therefore.logic.ui.components.TipBox
import com.therefore.logic.ui.theme.LocalPalette

private val TRAP_LESSONS = mapOf(
    Trap.AFFIRM to ("Affirming the consequent" to "From P → Q and Q you cannot conclude P. Q might be true for some other reason. Modus Ponens only runs forward: you need P to get Q."),
    Trap.DENY to ("Denying the antecedent" to "From P → Q and ¬P you cannot conclude ¬Q. The conditional says nothing about what happens when P is false. Modus Tollens needs ¬Q to get ¬P."),
    Trap.DS_RIGHT to ("DS cancels the left side" to "Disjunctive Syllogism removes the left part of an \"or\". If your \"not\" line denies the right part, use Commutation to swap the sides first."),
    Trap.SIMP_RIGHT to ("Simplification takes the left side" to "From P ∧ Q, Simplification gives P. To reach Q, use Commutation to write Q ∧ P first."),
    Trap.ADD_RIGHT to ("Addition keeps your line on the left" to "From P, Addition gives P ∨ (anything). Use Commutation afterwards if you need the new part on the left."),
    Trap.DN to ("Exact negations" to "Rules like Modus Tollens and Disjunctive Syllogism need the exact negation. The negation of ¬Q is ¬¬Q, not Q. Double Negation converts between them."),
    Trap.ALT to ("Naming the right rule" to "Some of your lines were correct but cited the wrong rule. Check the shape of the cited lines against the rule card before you pick a rule."),
    Trap.OTHER to ("Matching the pattern" to "Every rule has an exact pattern. Compare the main connective of each cited line with the rule card, and check that the matching parts are identical."),
)

@Composable
fun ResultScreen(state: GameState, onBack: () -> Unit, onNext: () -> Unit, onLesson: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val r = state.result ?: return
    val course = Curriculum.byId(state.courseId)
    val revealed = state.revealed

    val verdict = when {
        revealed -> "Solution shown"
        r.grade == Grades.S -> "S rank: a perfect proof"
        r.grade == "A" -> if (state.mistakes.isEmpty() && state.hints == 0) "Flawless proof" else "Strong proof"
        r.grade == "B" -> "Good proof"
        r.grade == "C" -> "Proof complete"
        else -> "You got there"
    }
    val advice = buildList {
        if (revealed) add("Read through the model proof below, then try a fresh puzzle in this course. Cover the explanation and see if you can justify each line yourself.")
        else {
            val diff = r.yourLines - r.modelLines
            add(
                when {
                    diff < 0 -> "Your proof was ${-diff} line${if (diff < -1) "s" else ""} shorter than the model proof."
                    diff > 0 -> "Your proof took $diff more line${if (diff > 1) "s" else ""} than the model proof. Compare the routes below to spot the shortcut."
                    else -> "Your proof was the same length as the model proof."
                },
            )
            if (state.mistakes.isEmpty()) add("Every line you submitted was correct.")
            if (state.isDaily && r.grade != Grades.S) add(
                "S rank needs no mistakes, no hints, and fewer than ${r.modelLines} lines: beating par. " +
                    listOfNotNull(
                        "you made ${state.mistakes.size} mistake${if (state.mistakes.size > 1) "s" else ""}".takeIf { state.mistakes.isNotEmpty() },
                        "you used hints".takeIf { state.hints > 0 },
                        "you used ${r.yourLines} lines".takeIf { r.yourLines >= r.modelLines },
                    ).joinToString(", ").replaceFirstChar { it.uppercase() } + ".",
            )
            if (state.hints > 0) add("You used ${state.hints} hint${if (state.hints > 1) "s" else ""}. Next time, try working backward from the conclusion before asking.")
        }
    }
    val lessons = state.mistakes.map { it.trap }.distinct().mapNotNull { TRAP_LESSONS[it] }
    val model = Proofs.numbered(state.puzzle)
    val rulesUsed = (state.puzzle.solution.map { it.rule } + state.lines.mapNotNull { it.rule }).distinct()

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Done") } },
                title = { Text(course.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GradeCard(r.grade, r.stars, r.score, verdict, revealed) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val time = if (r.seconds >= 60) "${r.seconds / 60}m ${r.seconds % 60}s" else "${r.seconds}s"
                    for ((v, l) in listOf((if (revealed) "–" else "${r.yourLines}") to "your lines", "${r.modelLines}" to "model lines",
                        "${state.mistakes.size}" to "mistakes", "${state.hints}" to "hints", time to "time")) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(p.surface).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(v, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                            Text(l, fontSize = 10.sp, color = p.muted, maxLines = 1)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    advice.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (lessons.isNotEmpty()) {
                item { SectionTitle("What to work on") }
                lessons.forEach { (h, b) -> item { TipBox(h, b) } }
            }
            if (state.mistakes.isNotEmpty()) item {
                Expandable("Your ${state.mistakes.size} rejected line${if (state.mistakes.size > 1) "s" else ""}") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.mistakes.forEach { m ->
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FormulaText(m.text)
                                    Tag((m.cites.map { it.toString() } + m.rule.abbr).joinToString(", "))
                                }
                                LogicText(m.message, style = MaterialTheme.typography.bodySmall, color = p.muted)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Model proof, explained") }
            item {
                Panel {
                    Ledger(
                        model.map { LedgerRow(it.formula.show(), it.line.justification, if (it.rule == null) null else Proofs.explain(it), it.rule == null, it.depth) },
                        state.puzzle.conclusion.show(),
                    )
                }
            }
            if (!revealed) item {
                Expandable("Your proof") {
                    val sc = Proofs.scopes(state.lines)
                    Ledger(state.lines.mapIndexed { i, l -> LedgerRow(l.formula.show(), l.justification, premise = l.rule == null, depth = sc.depth(i + 1)) }, state.puzzle.conclusion.show())
                }
            }
            if (!revealed && com.therefore.logic.engine.Lean.supports(state.puzzle.premises, state.lines)) item {
                Expandable("Your proof in Lean") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "The same proof in Lean, a language computers use to check proofs. Each line is a \"have\"; rules like MP and Simp become small pieces of code.",
                            style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted,
                        )
                        com.therefore.logic.ui.components.CodeBox(
                            com.therefore.logic.engine.Lean.proof(state.puzzle.premises, state.lines, state.puzzle.conclusion), lean = true,
                        )
                    }
                }
            }
            item { SectionTitle("Rules in this puzzle") }
            rulesUsed.forEach { rule -> item { RuleCard(rule) } }
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isDaily) {
                        Button(onClick = {
                            val text = "Therefore · ${course.title} ${state.date}\nGrade ${r.grade} ${"★".repeat(r.stars)}${"☆".repeat(3 - r.stars)} · " +
                                "${r.yourLines} lines (par ${r.modelLines}) · ${state.mistakes.size} mistakes · ${state.hints} hints"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, "Share your result"))
                        }, Modifier.fillMaxWidth()) { Text("Share result") }
                        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Done") }
                    } else {
                        Button(onClick = onNext, Modifier.fillMaxWidth()) {
                            Text(if (state.mode == com.therefore.logic.data.Mode.FREE) "Another level ${state.level} puzzle" else "Next practice puzzle")
                        }
                        OutlinedButton(onClick = onLesson, Modifier.fillMaxWidth()) { Text("Back to the course") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeCard(grade: String, stars: Int, score: Int, verdict: String, revealed: Boolean) {
    val p = LocalPalette.current
    val (fg, bg) = when (grade) {
        Grades.S -> p.star to p.hintSoft
        "A" -> p.good to p.goodSoft
        "B" -> p.accent to p.accentSoft
        Grades.REVEALED -> p.muted to p.sunk
        else -> p.hint to p.hintSoft
    }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(84.dp).scale(pop.value).clip(RoundedCornerShape(16.dp)).background(p.surface), contentAlignment = Alignment.Center) {
            Text(grade, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = fg)
        }
        Column {
            Eyebrow(if (revealed) "Not graded" else "Score $score")
            Text(verdict, style = MaterialTheme.typography.headlineSmall)
            Row {
                for (k in 1..3) Text("★", fontSize = 22.sp, color = if (k <= stars) p.star else p.line)
            }
        }
    }
}

@Composable
private fun Expandable(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Panel {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(if (open) "▲" else "▼", color = LocalPalette.current.muted)
            }
            AnimatedVisibility(open) { Column(Modifier.padding(top = 8.dp)) { content() } }
        }
    }
}
