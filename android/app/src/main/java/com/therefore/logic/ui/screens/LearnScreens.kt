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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
    onStats: () -> Unit = {},
) {
    val p = LocalPalette.current
    val today = LocalDate.now().toString()
    val day = progress.days[today]
    val predDay = progress.predicateDays[today]
    val solvedToday = listOfNotNull(day, predDay).count { !it.studied }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BrandHeader("∴", "Therefore", "Deductive logic proofs, one line at a time") {
                androidx.compose.material3.TextButton(onClick = onStats) { Text("Stats") }
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
        itemsIndexed(Curriculum.extraCourses) { i, c ->
            if (i == 0 || Curriculum.extraCourses[i - 1].section != c.section) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle(c.section ?: "")
                    SECTION_ABOUT[c.section]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
                }
            }
            CourseRow(Curriculum.courses.size + i + 1, c, progress, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { onCourse(c.id) }
        }
    }
}

private val SECTION_ABOUT = mapOf(
    Curriculum.SECTION_COMPUTERS to "Programs called SAT solvers check logic at a scale no person could. Learn the shape they use, their one rule, and how they search.",
    Curriculum.SECTION_TREES to "Truth trees test an argument by searching for a counterexample, one branch at a time.",
    Curriculum.SECTION_NEW to "Logic about what must be, what will be, and what can be shown.",
    Curriculum.SECTION_META to "What logic can and can't do: why the rules work, and the limits no rules can pass.",
    Curriculum.SECTION_MATH to "How mathematicians prove things about infinitely many numbers, and how computers check those proofs.",
    Curriculum.SECTION_LIFE to "Putting it to work on real arguments.",
)

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
                if (c.practisesProofs) Box(Modifier.padding(top = 5.dp)) { LevelDots(stats.level) }
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
fun DailyScreen(progress: Progress, inProgress: (String, LocalDate) -> Boolean, onStart: (String, LocalDate) -> Unit) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    var month by remember { mutableStateOf(java.time.YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BrandHeader("◷", "Daily Proofs", "${progress.streak} day streak" + if (progress.bestStreak > progress.streak) " · best ${progress.bestStreak}" else "") }
        item {
            Panel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                MonthCalendar(month, today, selected, progress, onMonth = { month = it }, onPick = { selected = it })
            }
        }
        item {
            val past = selected != today
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (past) selected.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")) else "Today",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (past) Text("Catch-up solves earn a grade but don't count toward your streak.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        item {
            DailyCard(
                "Daily proof", "A mixed puzzle that can use any of the 18 rules.",
                progress.days[selected.toString()], inProgress(Curriculum.DAILY_ID, selected), selected != today,
            ) { onStart(Curriculum.DAILY_ID, selected) }
        }
        item {
            DailyCard(
                "Daily predicate proof", "A puzzle with ∀ and ∃, using the quantifier rules.",
                progress.predicateDays[selected.toString()], inProgress(Curriculum.DAILY_PRED_ID, selected), selected != today,
            ) { onStart(Curriculum.DAILY_PRED_ID, selected) }
        }
    }
}

/**
 * A month of daily results. Each day shows two dots (∴ the daily proof, ∀ the predicate proof);
 * a ring around the day means it was solved on the day itself and counts toward the streak.
 */
@Composable
private fun MonthCalendar(
    month: java.time.YearMonth, today: LocalDate, selected: LocalDate, progress: Progress,
    onMonth: (java.time.YearMonth) -> Unit, onPick: (LocalDate) -> Unit,
) {
    val p = LocalPalette.current
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday first
    val cells = (0 until lead).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month.isAfter(java.time.YearMonth.from(today).minusMonths(12))) {
                Text("‹", fontSize = 26.sp, color = p.ink)
            }
            Text(
                month.month.getDisplayName(DateStyle.FULL, Locale.getDefault()) + " " + month.year,
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month.isBefore(java.time.YearMonth.from(today))) {
                Text("›", fontSize = 26.sp, color = if (month.isBefore(java.time.YearMonth.from(today))) p.ink else p.line)
            }
        }
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                for (c in 0 until 7) {
                    val d = week.getOrNull(c)
                    Box(Modifier.weight(1f).height(54.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d != null) DayCell(d, today, selected, progress, onPick)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(p.accent, "solved"); LegendDot(p.star, "S rank")
            Box(Modifier.size(14.dp).border(2.dp, p.accent, CircleShape))
            Text("solved on the day (streak)", style = MaterialTheme.typography.labelSmall, color = p.muted)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.muted)
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, selected: LocalDate, progress: Progress, onPick: (LocalDate) -> Unit) {
    val p = LocalPalette.current
    val future = d.isAfter(today)
    val results = listOf(progress.days[d.toString()], progress.predicateDays[d.toString()])
    val streakDay = results.any { it != null && !it.studied && it.onTime }
    val isSel = d == selected
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            .background(if (isSel) p.accentSoft else Color.Transparent)
            .border(if (streakDay) 2.dp else 0.dp, if (streakDay) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(enabled = !future) { onPick(d) }
            .semantics {
                contentDescription = "$d" + results.mapIndexed { i, r ->
                    val name = if (i == 0) "daily proof" else "predicate proof"
                    when { r == null -> ""; r.studied -> ", $name studied"; else -> ", $name grade ${r.grade}" }
                }.joinToString("") + if (streakDay) ", solved on the day" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${d.dayOfMonth}", fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
            color = when { future -> p.line; d == today -> p.accent; else -> p.ink },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            results.forEach { r ->
                val color = when {
                    r == null -> p.line.copy(alpha = if (future) 0f else 0.6f)
                    r.studied -> p.muted
                    r.grade == "S" -> p.star
                    else -> p.accent
                }
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun DailyCard(title: String, about: String, result: com.therefore.logic.data.DayResult?, inProgress: Boolean, past: Boolean, onStart: () -> Unit) {
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
                    result.studied -> "You studied the solution. Replay it any time."
                    result.grade == "S" -> "S rank today: a perfect proof."
                    else -> "Solved with grade ${result.grade}. Replay it to try for S; your best grade is kept."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val label = if (inProgress) "Resume" else if (result != null) "Replay" else if (past) "Catch up" else "Start"
            Button(onClick = onStart, Modifier.fillMaxWidth().semantics { contentDescription = "$label $title" }) {
                Text(label)
            }
        }
    }
}
