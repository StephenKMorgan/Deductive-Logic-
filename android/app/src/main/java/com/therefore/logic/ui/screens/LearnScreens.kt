@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.therefore.logic.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.content.Course
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.GameState
import com.therefore.logic.data.Progress
import com.therefore.logic.ui.components.CardShape
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.GradeBadge
import com.therefore.logic.ui.components.Ledger
import com.therefore.logic.ui.components.LedgerRow
import com.therefore.logic.ui.components.LevelDots
import com.therefore.logic.ui.components.LogicText
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.RuleCard
import com.therefore.logic.ui.components.SectionTitle
import com.therefore.logic.ui.components.Tag
import com.therefore.logic.ui.components.TipBox
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

@Composable
fun BrandHeader(mark: String, title: String, subtitle: String, action: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(p.accent), contentAlignment = Alignment.Center) {
            Text(mark, color = p.onAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.muted)
        }
        action()
    }
}

@Composable
fun HomeScreen(
    progress: Progress,
    game: GameState?,
    onResume: () -> Unit,
    onDaily: () -> Unit,
    onCourse: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val today = LocalDate.now().toString()
    val day = progress.days[today]
    val predDay = progress.predicateDays[today]
    val solvedToday = listOfNotNull(day, predDay).count { !it.studied }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BrandHeader("∴", "Therefore", "Deductive logic proofs, one line at a time") {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = p.muted) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (game != null && !game.done) {
                    CardRow(onResume, bg = p.accentSoft, border = p.accent) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Puzzle in progress")
                            Text("${Curriculum.byId(game.courseId).title} · line ${game.lines.size + 1}", style = MaterialTheme.typography.titleSmall)
                        }
                        Text("Resume →", color = p.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                CardRow(onDaily) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Daily proofs · $today")
                        Text(
                            when (solvedToday) { 0 -> "Two new proofs are ready"; 1 -> "1 of 2 solved today"; else -> "Both solved today" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("${progress.streak} day streak", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                    Text(if (solvedToday == 2) "View →" else "Start →", color = p.accent, fontWeight = FontWeight.SemiBold)
                }
                SectionTitle("Courses")
                Text(
                    "Each course adds new rules. Puzzles get longer as you solve more: the dots show your level.",
                    style = MaterialTheme.typography.bodyMedium, color = p.muted,
                )
            }
        }
        itemsIndexed(Curriculum.courses) { i, c ->
            if (c.advanced && !Curriculum.courses[i - 1].advanced) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("Advanced")
                    Text("Proofs with assumptions: suppose something, see where it leads, then close the subproof.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
                }
            }
            CourseRow(i + 1, c, progress, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { onCourse(c.id) }
        }
    }
}

@Composable
private fun CardRow(onClick: () -> Unit, bg: Color = LocalPalette.current.surface, border: Color = LocalPalette.current.line, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(bg).border(1.dp, border, CardShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CourseRow(n: Int, c: Course, progress: Progress, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val stats = progress.course(c.id)
    Row(
        modifier.fillMaxWidth().clip(CardShape).background(p.surface).border(1.dp, p.line, CardShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, p.line, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = p.muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium)
            Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = p.muted)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Tag(c.short)
                Box(Modifier.padding(top = 5.dp)) { LevelDots(stats.level) }
                val done = c.lessons.count { it.id in progress.lessonsDone }
                Text("$done/${c.lessons.size} lessons", style = MaterialTheme.typography.bodySmall, color = p.muted)
                if (stats.solved > 0) Text("${stats.solved} solved", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        GradeBadge(stats.best)
    }
}

/** The Predicate tab: courses on ∀, ∃ and the four quantifier rules. */
@Composable
fun PredicateScreen(progress: Progress, onCourse: (String) -> Unit) {
    val p = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BrandHeader("∀", "Predicate Logic", "All, some, and the things in between") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Propositional logic treats each statement as a single letter. Predicate logic looks inside: it can say that everything, or something, has a property. That's what you need for arguments like \"all humans are mortal; Socrates is human; so Socrates is mortal\".",
                    style = MaterialTheme.typography.bodyMedium, color = p.muted,
                )
                SectionTitle("Courses")
            }
        }
        itemsIndexed(Curriculum.predicateCourses) { i, c ->
            CourseRow(i + 1, c, progress, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { onCourse(c.id) }
        }
    }
}

@Composable
fun DailyScreen(progress: Progress, inProgress: (String) -> Boolean, onStart: (String) -> Unit) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BrandHeader("◷", "Daily Proofs", "${today.dayOfWeek.getDisplayName(DateStyle.FULL, Locale.getDefault())}, ${today.month.getDisplayName(DateStyle.FULL, Locale.getDefault())} ${today.dayOfMonth}")
        }
        item {
            Panel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("Everyone gets the same proofs today")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${progress.streak}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = p.accent)
                        Text(" day streak", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium)
                        if (progress.bestStreak > progress.streak)
                            Text("  · best ${progress.bestStreak}", Modifier.padding(bottom = 7.dp), color = p.muted)
                    }
                    Text("Solve either daily proof to keep your streak going.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    WeekRow(today, null, progress.days, "∴")
                    WeekRow(today, null, progress.predicateDays, "∀")
                }
            }
        }
        item {
            DailyCard(
                "Daily proof", "A mixed puzzle that can use any of the 18 rules.",
                progress.days[today.toString()], inProgress(Curriculum.DAILY_ID),
            ) { onStart(Curriculum.DAILY_ID) }
        }
        item {
            DailyCard(
                "Daily predicate proof", "A puzzle with ∀ and ∃, using the quantifier rules.",
                progress.predicateDays[today.toString()], inProgress(Curriculum.DAILY_PRED_ID),
            ) { onStart(Curriculum.DAILY_PRED_ID) }
        }
    }
}

/** One week of daily results; [mark] says which daily it is. */
@Composable
private fun WeekRow(today: LocalDate, label: String?, days: Map<String, com.therefore.logic.data.DayResult>, mark: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(mark, Modifier.width(26.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, color = p.muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 6 downTo 0) {
                val d = today.minusDays(i.toLong())
                val r = days[d.toString()]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (mark == "∴") Text(d.dayOfWeek.getDisplayName(DateStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.bodySmall, color = p.muted)
                    val solved = r != null && !r.studied
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (r?.grade == "S") p.star else if (solved) p.accent else if (r != null) p.sunk else Color.Transparent)
                            .border(1.5.dp, if (i == 0) p.accent else if (r == null) p.line else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(r?.grade ?: "", fontWeight = FontWeight.Bold, color = if (solved) p.onAccent else p.muted) }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(title: String, about: String, result: com.therefore.logic.data.DayResult?, inProgress: Boolean, onStart: () -> Unit) {
    val p = LocalPalette.current
    Panel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(about, style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
                if (result != null) GradeBadge(result.grade)
            }
            Text(
                when {
                    result == null -> "Beat par (fewer lines than the model proof) with no mistakes or hints for a rare S rank."
                    result.studied -> "You studied today's solution. A new one arrives tomorrow."
                    result.grade == "S" -> "S rank today: a perfect proof."
                    else -> "Solved with grade ${result.grade}. Replay it to try for S; your best grade is kept."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onStart, Modifier.fillMaxWidth()) {
                Text(if (inProgress) "Resume" else if (result != null) "Replay" else "Start")
            }
        }
    }
}
