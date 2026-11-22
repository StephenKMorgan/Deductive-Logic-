@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.therefore.logic.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therefore.logic.data.GameStats
import com.therefore.logic.games.Question
import com.therefore.logic.ui.GamesViewModel
import com.therefore.logic.ui.components.CardShape
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.FormulaText
import com.therefore.logic.ui.components.LogicText
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.SectionTitle
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono

enum class GameId(val route: String) { ISLAND("game/island"), QUIZ("game/quiz"), BOARD("game/board") }

@Composable
fun GamesScreen(vm: GamesViewModel, onOpen: (GameId) -> Unit) {
    val p = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle(GameStats())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BrandHeader("◆", "Logic Games", "Quick puzzles that put the rules to work") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameCard(
                    "Truth-tellers & Liars", "Some islanders always tell the truth, the rest always lie. Work out who's who from what they say.",
                    "${stats.islandSolved} solved · streak ${stats.islandStreak} · best ${stats.islandBestStreak}", "T|L",
                ) { onOpen(GameId.ISLAND) }
                GameCard(
                    "Valid or Not?", "60 seconds. Is the argument valid? Do the two formulas mean the same thing? Wrong answers cost 3 seconds.",
                    "Best score ${stats.quizBest} · ${stats.quizRounds} rounds", "✓✗",
                ) { onOpen(GameId.QUIZ) }
                GameCard(
                    "Switchboard", "Flip the switches until every statement lights up. Each board has exactly one solution.",
                    "${stats.boardSolved} solved · ${stats.boardPerfect} in the fewest flips", "I/O",
                ) { onOpen(GameId.BOARD) }
            }
        }
    }
}

@Composable
private fun GameCard(title: String, about: String, stats: String, mark: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(p.surface).border(1.dp, p.line, CardShape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Text(mark, fontFamily = Mono, fontWeight = FontWeight.Bold, color = p.accent, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(about, style = MaterialTheme.typography.bodyMedium, color = p.muted)
            Text(stats, style = MaterialTheme.typography.bodySmall, color = p.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GameScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val p = LocalPalette.current
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to games") } },
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
        content = content,
    )
}

// ======================= Truth-tellers & Liars =======================

@Composable
fun IslandScreen(vm: GamesViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val state by vm.island.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle(GameStats())
    LaunchedEffect(Unit) { if (vm.island.value == null) vm.newIsland() }
    GameScaffold("Truth-tellers & Liars", onBack) { pad ->
        val s = state ?: return@GameScaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Streak ${stats.islandStreak} · best ${stats.islandBestStreak}")
            Text("Truth-tellers only say true things. Liars only say false things. Who is who?", style = MaterialTheme.typography.bodyLarge)
            s.puzzle.statements.forEach { st ->
                val i = st.speaker
                val chosen = s.answers[i]
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(p.sunk), contentAlignment = Alignment.Center) {
                                Text(s.puzzle.names[i].take(1), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(s.puzzle.names[i], style = MaterialTheme.typography.titleMedium)
                                Text("“${st.text}”", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((label, value) in listOf("Truth-teller" to true, "Liar" to false)) {
                                val on = chosen == value
                                val bg by animateColorAsState(if (on) (if (value) p.good else p.bad) else p.sunk, label = "choice")
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                                        .clickable(enabled = !s.correct) { vm.answer(i, value) }
                                        .semantics { contentDescription = "${s.puzzle.names[i]}: $label${if (on) ", chosen" else ""}" },
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, color = if (on) p.surface else p.muted, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
            when {
                s.correct -> {
                    Panel(color = p.goodSoft, border = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Solved!", style = MaterialTheme.typography.titleMedium, color = p.good)
                            TruthTellersExplain(s)
                        }
                    }
                    Button(onClick = { vm.newIsland() }, Modifier.fillMaxWidth()) { Text("Next island") }
                }
                else -> {
                    if (s.mistake != null) Panel(color = p.badSoft, border = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Not quite", style = MaterialTheme.typography.titleSmall, color = p.bad)
                            Text(s.mistake)
                        }
                    }
                    Button(onClick = vm::checkIsland, Modifier.fillMaxWidth(), enabled = s.answers.none { it == null }) { Text("Check") }
                    if (s.mistake != null) OutlinedButton(onClick = { vm.newIsland() }, Modifier.fillMaxWidth()) { Text("Skip to a new island") }
                }
            }
        }
    }
}

@Composable
private fun TruthTellersExplain(s: com.therefore.logic.ui.IslandState) {
    val p = LocalPalette.current
    com.therefore.logic.games.TruthTellers.explanation(s.puzzle).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    Text("In logic: each islander tells the truth exactly when their statement is true.", style = MaterialTheme.typography.bodySmall, color = p.muted)
    s.puzzle.statements.forEach { st ->
        FormulaText(s.puzzle.constraint(st).show(), style = FormulaStyle.copy(fontSize = 14.sp))
    }
    Text(
        s.puzzle.names.withIndex().joinToString(" · ") { (i, n) -> "${'A' + i} = $n tells the truth" },
        style = MaterialTheme.typography.bodySmall, color = p.muted,
    )
}

// ======================= Valid or Not? =======================

@Composable
fun QuizScreen(vm: GamesViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val s by vm.quiz.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle(GameStats())
    GameScaffold("Valid or Not?", { vm.leaveQuiz(); onBack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                s.over -> {
                    Eyebrow("Time's up")
                    Text("${s.score} points", style = MaterialTheme.typography.headlineMedium)
                    Text(if (s.score >= stats.quizBest && s.score > 0) "A new best!" else "Best: ${stats.quizBest}", color = p.muted)
                    Text("${s.asked} questions answered, ${s.missed.size} missed.", style = MaterialTheme.typography.bodyMedium)
                    if (s.missed.isNotEmpty()) {
                        SectionTitle("Review your misses")
                        s.missed.forEach { q -> Panel { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { QuestionView(q); LogicText(q.explanation, style = MaterialTheme.typography.bodySmall, color = p.muted) } } }
                    }
                    Button(onClick = vm::startQuiz, Modifier.fillMaxWidth()) { Text("Play again") }
                }
                !s.playing -> {
                    Text("Answer as many as you can in 60 seconds.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Arguments: is it valid (the conclusion must be true whenever the premises are)? Pairs: do the two formulas mean the same thing? " +
                            "Every 5 right in a row earns a bonus point, and a wrong answer costs 3 seconds.",
                        style = MaterialTheme.typography.bodyMedium, color = p.muted,
                    )
                    Text("Best score: ${stats.quizBest}", fontWeight = FontWeight.SemiBold, color = p.accent)
                    Button(onClick = vm::startQuiz, Modifier.fillMaxWidth()) { Text("Start") }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.timeLeft}s", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (s.timeLeft <= 10) p.bad else p.ink)
                        Spacer(Modifier.weight(1f))
                        Text("Score ${s.score}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (s.streak >= 3) Text("  ·  ${s.streak} in a row", color = p.accent)
                    }
                    LinearProgressIndicator(progress = { s.timeLeft / com.therefore.logic.ui.QuizState.ROUND_SECONDS.toFloat() }, Modifier.fillMaxWidth(), color = p.accent, trackColor = p.sunk)
                    val q = s.question ?: return@Column
                    Panel { QuestionView(q) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val (yes, no) = if (q is Question.Argument) "Valid" to "Invalid" else "Same" to "Different"
                        Button(onClick = { vm.answerQuiz(true) }, Modifier.weight(1f).height(56.dp)) { Text(yes, fontSize = 18.sp) }
                        OutlinedButton(onClick = { vm.answerQuiz(false) }, Modifier.weight(1f).height(56.dp)) { Text(no, fontSize = 18.sp) }
                    }
                    if (s.lastRight != null) Panel(color = if (s.lastRight == true) p.goodSoft else p.badSoft, border = false) {
                        Column {
                            Text(if (s.lastRight == true) "Last answer: right" else "Last answer: wrong", fontWeight = FontWeight.Bold, color = if (s.lastRight == true) p.good else p.bad)
                            if (s.lastRight == false) LogicText(s.lastExplanation.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionView(q: Question) {
    val p = LocalPalette.current
    when (q) {
        is Question.Argument -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("Is this argument valid?")
            q.premises.forEach { FormulaText(it.show(), style = FormulaStyle.copy(fontSize = 18.sp)) }
            FormulaText("∴ ${q.conclusion}", style = FormulaStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = p.accent)
        }
        is Question.Equivalent -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Do these mean the same thing?")
            FormulaText(q.left.show(), style = FormulaStyle.copy(fontSize = 19.sp))
            Text("and", color = p.muted)
            FormulaText(q.right.show(), style = FormulaStyle.copy(fontSize = 19.sp))
        }
    }
}

// ======================= Switchboard =======================

@Composable
fun BoardScreen(vm: GamesViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val state by vm.board.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.board.value == null) vm.newBoard() }
    GameScaffold("Switchboard", onBack) { pad ->
        val s = state ?: return@GameScaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make every statement true at once.", style = MaterialTheme.typography.titleMedium)
            Text("Flips: ${s.moves} · fewest possible: ${s.minMoves}", color = p.muted)
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.board.constraints.forEachIndexed { i, c ->
                        val ok = s.satisfied[i]
                        val bg by animateColorAsState(if (ok) p.goodSoft else p.sunk, label = "lamp")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (ok) p.good else p.line))
                            FormulaText(c.show(), Modifier.padding(start = 12.dp).weight(1f), style = FormulaStyle.copy(fontSize = 18.sp))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                s.board.letters.forEach { l ->
                    val on = s.switches.getValue(l)
                    val bg by animateColorAsState(if (on) p.accent else p.surface, label = "switch")
                    Column(
                        Modifier.weight(1f).clip(CardShape).background(bg).border(1.5.dp, if (on) p.accent else p.line, CardShape)
                            .clickable(enabled = !s.solved) { vm.flip(l) }
                            .semantics { contentDescription = "Switch $l, ${if (on) "true" else "false"}" }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$l", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (on) p.onAccent else p.ink)
                        Text(if (on) "TRUE" else "FALSE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) p.onAccent else p.muted)
                    }
                }
            }
            if (s.solved) {
                Panel(color = p.goodSoft, border = false) {
                    Text(
                        if (s.moves <= s.minMoves) "All lit, in the fewest flips possible!" else "All lit in ${s.moves} flips. The fewest possible was ${s.minMoves}.",
                        color = p.good, fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(onClick = { vm.newBoard() }, Modifier.fillMaxWidth()) { Text("Next board") }
            } else {
                Text("Tip: start with a statement that only allows one choice, like a single letter or ¬(P ∧ Q).", style = MaterialTheme.typography.bodySmall, color = p.muted, textAlign = TextAlign.Start)
            }
        }
    }
}
