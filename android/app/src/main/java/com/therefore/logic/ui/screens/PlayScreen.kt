@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.therefore.logic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.FeedbackKind
import com.therefore.logic.data.GameState
import com.therefore.logic.engine.Parser
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.RuleKind
import com.therefore.logic.ui.GameViewModel
import com.therefore.logic.game.BoardActions
import com.therefore.logic.engine.Proofs
import com.therefore.logic.engine.isVariable
import com.therefore.logic.ui.components.CardShape
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.FormulaText
import com.therefore.logic.ui.components.LogicText
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.LocalPlainText
import com.therefore.logic.ui.theme.Mono
import com.therefore.logic.ui.theme.inNotation

@Composable
fun PlayScreen(vm: GameViewModel, state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val course = Curriculum.byId(state.courseId)
    var confirmGiveUp by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Leave puzzle") } },
                title = {
                    Column {
                        Text(course.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        val sub = when (state.mode) {
                            com.therefore.logic.data.Mode.DAILY -> "${state.date} · Par ${state.puzzle.solution.size} lines"
                            com.therefore.logic.data.Mode.FREE -> "Free play · Level ${state.level}" +
                                (state.lessonId?.let { id -> course.lessons.firstOrNull { it.id == id }?.let { " · ${it.title}" } } ?: "")
                            else -> "Practice · Level ${state.level}"
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    TextButton(onClick = vm::hint) { Text("Hint") }
                    TextButton(onClick = { confirmGiveUp = true }) { Text("Solution") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ProofList(vm, state, Modifier.weight(1f))
            Composer(vm, state)
        }
    }

    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text("Show the solution?") },
            text = { Text("You'll see the model proof explained line by line. This puzzle won't be graded or count toward your level.") },
            confirmButton = { TextButton(onClick = { confirmGiveUp = false; vm.giveUp() }) { Text("Show solution") } },
            dismissButton = { TextButton(onClick = { confirmGiveUp = false }) { Text("Keep trying") } },
        )
    }
}

@Composable
fun ProofList(vm: BoardActions, s: GameState, modifier: Modifier, showGoal: Boolean = true) {
    val p = LocalPalette.current
    val list = rememberLazyListState()
    val scopes = remember(s.lines) { Proofs.scopes(s.lines) }
    val clashes = remember(s.lines) { Proofs.contradictions(s.lines) }
    LaunchedEffect(s.lines.size) { list.animateScrollToItem(s.lines.size + 1) }
    LazyColumn(modifier, state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        if (showGoal) item {
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(p.surface).border(1.dp, p.line, CardShape).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Eyebrow("Prove")
                FormulaText("∴ ${s.puzzle.conclusion}", style = FormulaStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), color = p.accent)
            }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(s.lines, key = { i, _ -> i }) { i, line ->
            val n = i + 1
            val depth = scopes.depth(n)
            val usable = scopes.available(n)
            val selected = n in s.cites
            val clash = n in clashes
            val bg by animateColorAsState(
                when {
                    selected -> p.accentSoft
                    clash -> p.badSoft
                    else -> Color.Transparent
                },
                label = "cite",
            )
            if (i > 0) HorizontalDivider(color = if (i == s.premiseCount) p.ink.copy(alpha = 0.5f) else p.line, thickness = if (i == s.premiseCount) 1.5.dp else 0.5.dp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                // One bar per open assumption, like the indented subproofs in a textbook.
                repeat(depth) {
                    Box(Modifier.padding(start = 6.dp, end = 4.dp).width(2.dp).fillMaxHeight().background(p.accent.copy(alpha = 0.6f)))
                }
                Row(
                    Modifier
                        .weight(1f)
                        .alpha(if (usable) 1f else 0.45f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .then(if (selected) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { vm.toggleCite(n) }
                        .semantics { contentDescription = "Line $n, ${line.formula}${if (clash) ", contradiction" else ""}. Tap to cite." }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$n.", Modifier.width(28.dp), style = FormulaStyle, color = p.muted, textAlign = TextAlign.End)
                    FormulaText(line.formula.show(), Modifier.weight(1f).padding(start = 8.dp), color = if (clash) p.bad else Color.Unspecified)
                    Text(line.justification, style = FormulaStyle.copy(fontSize = 13.sp), color = if (clash) p.bad else p.muted)
                }
                AnimatedVisibility(selected) {
                    OutlinedButton(
                        onClick = { vm.copyLine(n) },
                        Modifier.padding(start = 6.dp).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("Copy", fontSize = 12.sp) }
                }
            }
        }
        item {
            HorizontalDivider(color = p.line, thickness = 0.5.dp)
            val preview = Parser.parseOrNull(s.tokens.joinToString(""))?.show() ?: s.tokens.joinToString(" ")
            val ghostDepth = scopes.open.size + if (s.rule?.kind == RuleKind.ASSUMPTION) 1 else if (s.rule?.kind == RuleKind.CLOSE) -1 else 0
            Row(Modifier.alpha(0.6f).padding(start = (6 + 12 * ghostDepth.coerceAtLeast(0)).dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.lines.size + 1}.", Modifier.width(28.dp), style = FormulaStyle, color = p.muted, textAlign = TextAlign.End)
                FormulaText(preview.ifEmpty { "…" }, Modifier.weight(1f).padding(start = 8.dp))
                val just = if (s.rule != null && !s.rule.isStandard) s.rule.abbr
                else listOfNotNull(s.cites.sorted().joinToString(", ").ifEmpty { null }, s.rule?.abbr).joinToString(", ")
                Text(just, style = FormulaStyle.copy(fontSize = 13.sp), color = p.muted)
            }
        }
    }
}

@Composable
fun Composer(vm: BoardActions, s: GameState) {
    val p = LocalPalette.current
    val rules = s.rules.ifEmpty { Curriculum.boardRules(Curriculum.byId(s.courseId).spec.rules) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        FeedbackBar(vm, s)
        FormulaInput(vm, s)
        if (rules.size <= 5) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in rules) FilterChip(
                    selected = s.rule == r,
                    onClick = { vm.selectRule(if (s.rule == r) null else r) },
                    label = { Text(r.abbr, fontFamily = Mono, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = p.accent, selectedLabelColor = p.onAccent),
                )
            }
        } else RulePicker(rules, s.rule, vm::selectRule)
        RuleHelp(s)
        Keypad(vm, s)
    }
}

@Composable
private fun FeedbackBar(vm: BoardActions, s: GameState) {
    val p = LocalPalette.current
    val fb = s.feedback
    // In lessons the coach gives the instructions, so the generic tip is left out.
    val untouched = s.mode != com.therefore.logic.data.Mode.LESSON &&
        s.tokens.isEmpty() && s.cites.isEmpty() && s.rule == null && s.lines.size == s.premiseCount
    val (text, kind) = when {
        fb != null -> fb.text to fb.kind
        untouched -> "Tap lines to cite them, choose a rule, then write the new line." to FeedbackKind.INFO
        else -> return
    }
    val (fg, bg) = when (kind) {
        FeedbackKind.GOOD -> p.good to p.goodSoft
        FeedbackKind.BAD -> p.bad to p.badSoft
        FeedbackKind.HINT -> p.hint to p.hintSoft
        FeedbackKind.INFO -> p.muted to p.sunk
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).heightIn(max = 120.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LogicText(text, style = MaterialTheme.typography.bodyMedium, color = fg)
            fb?.detail?.let { FormulaText(it, style = FormulaStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = fg) }
        }
        if (fb?.fill != null) {
            OutlinedButton(onClick = vm::fillHint, Modifier.padding(start = 8.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Fill it in", color = fg, fontSize = 13.sp)
            }
        }
    }
}

/** The new line. Tap a symbol to put the cursor on that side of it; tap empty space for the end. */
@Composable
private fun FormulaInput(vm: BoardActions, s: GameState) {
    val p = LocalPalette.current
    val plain = LocalPlainText.current
    val blink = rememberInfiniteTransition(label = "caret")
    val caretAlpha by blink.animateFloat(
        1f, 0f, infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500 }, RepeatMode.Restart), label = "caret",
    )
    val caret = @Composable { Box(Modifier.padding(horizontal = 1.dp).width(2.dp).height(26.dp).alpha(caretAlpha).background(p.accent)) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.sunk)
            .border(1.5.dp, p.line, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { vm.setCaret(s.tokens.size) }
            .semantics { contentDescription = "New line formula. Tap a symbol to move the cursor." }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (s.tokens.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                caret()
                Text("Write line ${s.lines.size + 1}", Modifier.padding(start = 4.dp), color = p.muted)
            }
        } else FlowRow(verticalArrangement = Arrangement.Center) {
            s.tokens.forEachIndexed { i, t ->
                if (i == s.caret) caret()
                val op = t in "∧∨→↔"
                Text(
                    t.toString().inNotation(plain),
                    Modifier
                        .pointerInput(i) { detectTapGestures { o -> vm.setCaret(if (o.x > size.width / 2f) i + 1 else i) } }
                        .padding(horizontal = if (op) 6.dp else 1.dp, vertical = 4.dp),
                    style = FormulaStyle.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                    color = if (op) p.accent else p.ink,
                )
            }
            if (s.caret == s.tokens.size) caret()
        }
    }
}

@Composable
private fun RulePicker(rules: List<Rule>, selected: Rule?, onSelect: (Rule?) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.abbr} · ${it.title}" } ?: "Choose a rule…",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (selected != null) p.accentSoft else p.bg,
                unfocusedContainerColor = if (selected != null) p.accentSoft else p.bg,
                unfocusedBorderColor = if (selected != null) p.accent else p.line,
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth().semantics { contentDescription = "Rule picker" },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, inGroup) in listOf<Pair<String, (Rule) -> Boolean>>(
                "Quantifier rules" to { it.isQuantifierRule },
                "Identity rules" to { it.isIdentityRule },
                "Rules of inference" to { it.kind == RuleKind.INFERENCE && !it.isQuantifierRule && !it.isIdentityRule },
                "Replacement rules" to { it.kind == RuleKind.REPLACEMENT && !it.isQuantifierRule && !it.isIdentityRule },
                "Assumptions" to { it.kind == RuleKind.ASSUMPTION || it.kind == RuleKind.CLOSE },
            )) {
                val group = rules.filter(inGroup)
                if (group.isEmpty()) continue
                Eyebrow(label, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                for (r in group) DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.abbr, Modifier.width(56.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, color = p.accent)
                            Text(r.title)
                        }
                    },
                    onClick = { onSelect(r); open = false },
                )
            }
        }
    }
}

@Composable
private fun RuleHelp(s: GameState) {
    val p = LocalPalette.current
    val r = s.rule
    if (r == null) {
        Text("Citing: ${s.cites.sorted().joinToString(", ").ifEmpty { "none yet" }}", style = MaterialTheme.typography.bodySmall, color = p.muted)
    } else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when (r.kind) {
                RuleKind.ASSUMPTION -> "${r.title} · write what you assume"
                RuleKind.CLOSE -> "${r.title} · closes the latest assumption, no citing needed"
                else -> "${r.title} · " + when (r.lines) { 0 -> "cite no lines"; 1 -> "cite one line"; else -> "cite two lines" }
            },
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        if (r.isStandard) FormulaText("${r.from} ${if (r.kind == RuleKind.REPLACEMENT) "⟺" else "⟹"} ${r.to}", style = FormulaStyle.copy(fontSize = 13.sp))
    }
}

private sealed interface KeySpec {
    data class Sym(val t: Char, val label: String) : KeySpec
    data class Fn(val label: String, val desc: String, val action: (BoardActions) -> Unit, val enabled: Boolean = true) : KeySpec
    data object AddLine : KeySpec
    /** An empty cell that pads out a row. */
    data object Blank : KeySpec
}

@Composable
private fun Keypad(vm: BoardActions, s: GameState) {
    val used = (s.puzzle.premises + s.puzzle.conclusion).flatMap { it.atoms() }
    val minLetters = if (s.mode == com.therefore.logic.data.Mode.LESSON) 4 else Curriculum.byId(s.courseId).spec.letters
    val count = maxOf(minLetters, used.maxOfOrNull { "PQRSTU".indexOf(it) + 1 } ?: 0)
    val predicateLogic = (s.puzzle.premises + s.puzzle.conclusion).any { it.isPredicateLogic }
    val keys = buildList<KeySpec> {
        if (predicateLogic) {
            // Predicate letters, then the variable x, the names in use plus the next new one (for EI), then ∀ ∃.
            val all = s.puzzle.premises + s.puzzle.conclusion + s.lines.map { it.formula }
            all.flatMap { it.predicates() }.distinct().sorted().forEach { add(KeySpec.Sym(it, "predicate $it")) }
            all.flatMap { it.terms() }.filter(::isVariable).plus('x').distinct().sorted().forEach { add(KeySpec.Sym(it, "variable $it")) }
            val names = all.flatMap { it.names() }.toSortedSet()
            val next = ('a'..'w').first { it != 'v' && it !in names }
            (names.take(3) + next).sorted().forEach { add(KeySpec.Sym(it, "name $it")) }
            if (s.rules.any { it.isIdentityRule } || all.any { "=" in it.show() }) add(KeySpec.Sym('=', "equals"))
            add(KeySpec.Sym('∀', "for all"))
            add(KeySpec.Sym('∃', "there exists"))
        } else "PQRSTU".take(count).forEach { add(KeySpec.Sym(it, it.toString())) }
        listOf('¬' to "not", '∧' to "and", '∨' to "or", '→' to "if then", '↔' to "if and only if", '(' to "open bracket", ')' to "close bracket")
            .forEach { (t, d) -> add(KeySpec.Sym(t, d)) }
        add(KeySpec.Fn("◀", "Move cursor left", { it.moveCaret(-1) }))
        add(KeySpec.Fn("▶", "Move cursor right", { it.moveCaret(1) }))
        add(KeySpec.Fn("⌫", "Delete", { it.backspace() }))
        add(KeySpec.Fn("Undo", "Remove last line", { it.undoLine() }, s.lines.size > s.premiseCount))
        add(KeySpec.AddLine)
    }
    val rows = ArrayList<List<Pair<KeySpec, Int>>>()
    var row = ArrayList<Pair<KeySpec, Int>>()
    for (k in keys) {
        // "Add line" needs room: if fewer than three cells are left, pad the row and give it its own.
        if (k is KeySpec.AddLine && 7 - row.size < 3 && row.isNotEmpty()) {
            row.add(KeySpec.Blank to (7 - row.size)); rows += row; row = ArrayList()
        }
        val span = if (k is KeySpec.AddLine) 7 - row.size else 1
        row.add(k to span)
        if (row.sumOf { it.second } == 7) { rows += row; row = ArrayList() }
    }
    if (row.isNotEmpty()) rows += row
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (r in rows) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for ((k, span) in r) {
                if (k is KeySpec.Blank) Spacer(Modifier.weight(span.toFloat())) else Key(vm, k, Modifier.weight(span.toFloat()))
            }
        }
    }
}

@Composable
private fun Key(vm: BoardActions, k: KeySpec, modifier: Modifier) {
    val p = LocalPalette.current
    val plain = LocalPlainText.current
    val shape = RoundedCornerShape(10.dp)
    val (label, desc, enabled, primary) = when (k) {
        is KeySpec.Sym -> Quad(k.t.toString().inNotation(plain), k.label, true, false)
        is KeySpec.Fn -> Quad(k.label, k.desc, k.enabled, false)
        KeySpec.AddLine -> Quad("Add line", "Add line", true, true)
        KeySpec.Blank -> Quad("", "", false, false)
    }
    val isOp = k is KeySpec.Sym && k.t in "¬∧∨→↔∀∃"
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (primary) p.accent else p.bg)
            .border(1.dp, if (primary) p.accent else p.line, shape)
            .clickable(enabled = enabled) {
                when (k) {
                    is KeySpec.Sym -> vm.key(k.t)
                    is KeySpec.Fn -> k.action(vm)
                    KeySpec.AddLine -> vm.addLine()
                    KeySpec.Blank -> {}
                }
            }
            .semantics { contentDescription = desc }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = if (k is KeySpec.Sym) Mono else null,
            fontWeight = FontWeight.SemiBold,
            fontSize = when { primary -> 16.sp; k is KeySpec.Fn && k.label == "Undo" -> 13.sp; else -> 19.sp },
            color = when { primary -> p.onAccent; isOp -> p.accent; k is KeySpec.Fn -> p.muted; else -> p.ink },
        )
    }
}

private data class Quad(val label: String, val desc: String, val enabled: Boolean, val primary: Boolean)
