@file:OptIn(ExperimentalMaterial3Api::class)

package com.therefore.logic.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.Progress
import com.therefore.logic.data.Settings
import com.therefore.logic.data.ThemeMode
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.RuleKind
import com.therefore.logic.reminder.Reminders
import com.therefore.logic.ui.components.Ledger
import com.therefore.logic.ui.components.LedgerRow
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.RuleCard
import com.therefore.logic.ui.components.SectionTitle
import com.therefore.logic.ui.components.TipBox
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun RulesScreen() {
    val p = LocalPalette.current
    val symbols = listOf(
        listOf("¬", "Negation", "\"not P\"", "~P"),
        listOf("∧", "Conjunction", "\"P and Q\"", "P & Q"),
        listOf("∨", "Disjunction", "\"P or Q\" (or both)", "P v Q"),
        listOf("→", "Conditional", "\"if P, then Q\"", "P -> Q"),
        listOf("↔", "Biconditional", "\"P if and only if Q\"", "P <-> Q"),
        listOf("∴", "Therefore", "the conclusion", ":."),
    )
    Column(Modifier.fillMaxSize()) {
    BrandHeader("§", "Rules", "The cheat sheet, always at hand")
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Symbols") }
        item {
            Panel {
                Column {
                    symbols.forEachIndexed { i, (sym, name, read, plain) ->
                        if (i > 0) HorizontalDivider(color = p.line, thickness = 0.5.dp)
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sym, Modifier.width(36.dp), fontFamily = Mono, fontSize = 20.sp, color = p.accent)
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(read, style = MaterialTheme.typography.bodySmall, color = p.muted)
                            }
                            Text(plain, fontFamily = Mono, color = p.muted)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Rules of inference") }
        item { Text("These apply to whole lines only: from the lines on the left, you may write the line on the right.", style = MaterialTheme.typography.bodyMedium) }
        items(Rule.entries.filter { it.kind == RuleKind.INFERENCE && !it.isQuantifierRule }) { RuleCard(it) }
        item { TipBox("Not valid", "From P → Q and Q, concluding P (affirming the consequent). From P → Q and ¬P, concluding ¬Q (denying the antecedent).", warn = true) }
        item { SectionTitle("Replacement rules") }
        item { Text("Each side means the same thing, so you can swap one for the other in either direction, even inside part of a line.", style = MaterialTheme.typography.bodyMedium) }
        items(Rule.entries.filter { it.kind == RuleKind.REPLACEMENT && !it.isQuantifierRule }) { RuleCard(it) }
        item { SectionTitle("Assumptions") }
        item { Text("Open a subproof with an assumption, then close it. Indented lines can't be cited after the subproof closes.", style = MaterialTheme.typography.bodyMedium) }
        items(listOf(Rule.ACP, Rule.CP, Rule.AIP, Rule.IP)) { RuleCard(it) }
        item { SectionTitle("Predicate logic") }
        item {
            Text(
                "Fx: x has property F. a, b, c name particular things; x, y, z are variables. ∀x: for every x. ∃x: for at least one x. " +
                    "EI must use a name that hasn't appeared yet, and UG can only generalize a variable, never a name.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(Rule.entries.filter { it.isQuantifierRule }) { RuleCard(it) }
        item { SectionTitle("How to write a proof") }
        item { Text("Number every line. The premises come first. Each new line names the rule you used and the line numbers it came from. You're done when the conclusion appears on its own line.", style = MaterialTheme.typography.bodyMedium) }
        item {
            Panel {
                Ledger(
                    listOf(
                        LedgerRow("P ∨ Q", "Premise", premise = true), LedgerRow("Q ∧ S", "Premise", premise = true),
                        LedgerRow("S ∧ Q", "2, Com"), LedgerRow("S", "3, Simp"), LedgerRow("S ∨ P", "4, Add"), LedgerRow("P ∨ S", "5, Com"),
                    ),
                    "P ∨ S",
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Work backward from the conclusion: ask what line would let you write it.",
                    "Simplification only takes the left side of an \"and\", so use Commutation first if you need the right side.",
                    "Not every premise has to be used.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
}

@Composable
fun SettingsScreen(
    settings: Settings,
    progress: Progress,
    onBack: () -> Unit,
    onPlainText: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onReminder: (Boolean, Int) -> Unit,
    onReset: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onReminder(true, settings.reminderAt)
    }
    fun enableReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(context))
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onReminder(true, settings.reminderAt)
    }
    val time = LocalTime.of(settings.reminderAt / 60, settings.reminderAt % 60)
    val timeText = time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle("Display") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Notation", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(!settings.plainText, { onPlainText(false) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("¬ ∧ ∨ →", fontFamily = Mono) }
                                SegmentedButton(settings.plainText, { onPlainText(true) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("~ & v ->", fontFamily = Mono) }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Theme", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                ThemeMode.entries.forEachIndexed { i, m ->
                                    SegmentedButton(settings.theme == m, { onTheme(m) }, SegmentedButtonDefaults.itemShape(i, 3)) {
                                        Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Feedback") }
            item {
                Panel {
                    SettingRow("Vibration", "Buzz on keys, accepted lines, mistakes and solved proofs", settings.haptics, onHaptics)
                }
            }
            item { SectionTitle("Daily reminder") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingRow("Remind me", "A notification when today's proof isn't solved yet", settings.reminder) { on ->
                            if (on) enableReminder() else onReminder(false, settings.reminderAt)
                        }
                        if (settings.reminder) {
                            HorizontalDivider(color = p.line)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    TimePickerDialog(context, { _, h, m -> onReminder(true, h * 60 + m) }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(timeText, color = p.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Your progress") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatRow("Proofs solved", "${progress.totalSolved}")
                        StatRow("Without mistakes or hints", "${progress.perfect}")
                        StatRow("Daily streak", "${progress.streak} (best ${progress.bestStreak})")
                        HorizontalDivider(color = p.line)
                        Curriculum.allCourses.forEach { c ->
                            val s = progress.course(c.id)
                            StatRow(c.title, "${s.solved} solved · level ${s.level}" + (s.best?.let { " · best $it" } ?: ""))
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth()) { Text("Reset progress", color = p.bad) }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("This clears every course level, grade, your daily streak and the puzzle in progress. It can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset", color = p.bad) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.muted)
    }
}
