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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonDef
import com.therefore.logic.data.Progress
import com.therefore.logic.ui.components.CardShape
import com.therefore.logic.ui.components.Eyebrow
import com.therefore.logic.ui.components.GradeBadge
import com.therefore.logic.ui.components.LevelDots
import com.therefore.logic.ui.components.SectionTitle
import com.therefore.logic.ui.components.Tag
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono

/** What the course page can start. */
sealed interface CourseAction {
    data object Practice : CourseAction
    data class Lesson(val lessonId: String) : CourseAction
    data class FreePlay(val lessonId: String?, val level: Int) : CourseAction
}

@Composable
fun CourseScreen(courseId: String, progress: Progress, resumeLabel: String?, onBack: () -> Unit, onAction: (CourseAction) -> Unit) {
    val p = LocalPalette.current
    val c = Curriculum.byId(courseId)
    val stats = progress.course(courseId)
    val n = when (c.track) {
        com.therefore.logic.content.Track.PREDICATE -> Curriculum.predicateCourses.indexOf(c) + 1
        com.therefore.logic.content.Track.COMPUTING -> Curriculum.courses.size + Curriculum.computingCourses.indexOf(c) + 1
        else -> Curriculum.courses.indexOf(c) + 1
    }
    // Free play dialog: which lesson (null = whole course).
    var freePlayFor by rememberSaveable { mutableStateOf<String?>(null) }
    var freePlayOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to courses") } },
                title = {
                    Text(
                        when {
                            c.track == com.therefore.logic.content.Track.PREDICATE -> "Predicate course $n"
                            c.track == com.therefore.logic.content.Track.COMPUTING -> "Computer logic · course $n"
                            c.advanced -> "Advanced course $n"
                            else -> "Course $n"
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.title, style = MaterialTheme.typography.headlineMedium)
                    Text(c.blurb, style = MaterialTheme.typography.bodyLarge, color = p.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                        Tag(c.short)
                        Box(Modifier.padding(top = 5.dp)) { LevelDots(stats.level) }
                        Text("Level ${stats.level} of 5", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(CardShape).background(p.accentSoft).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Practice")
                            Text(
                                if (stats.level < 5) "Solve ${2 * stats.level - stats.solved} more to reach level ${stats.level + 1}" else "Top level reached",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text("${stats.solved} solved", style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                        GradeBadge(stats.best)
                    }
                    Button(onClick = { onAction(CourseAction.Practice) }, Modifier.fillMaxWidth()) {
                        Text(resumeLabel ?: "Practice · Level ${stats.level}")
                    }
                }
            }
            item { SectionTitle("Lessons") }
            itemsIndexed(c.lessons) { i, lesson ->
                LessonCard(
                    i + 1, lesson, lesson.id in progress.lessonsDone,
                    onStart = { onAction(CourseAction.Lesson(lesson.id)) },
                    onFreePlay = { freePlayFor = lesson.id; freePlayOpen = true },
                )
            }
            item {
                OutlinedButton(onClick = { freePlayFor = null; freePlayOpen = true }, Modifier.fillMaxWidth()) {
                    Text("Free play with every rule in this course")
                }
            }
        }
    }

    if (freePlayOpen) {
        val lesson = c.lessons.firstOrNull { it.id == freePlayFor }
        LevelPicker(
            title = "Free play" + (lesson?.let { ": ${it.title}" } ?: ""),
            unlocked = stats.level,
            onDismiss = { freePlayOpen = false },
            onPick = { level -> freePlayOpen = false; onAction(CourseAction.FreePlay(lesson?.id, level)) },
        )
    }
}

@Composable
private fun LessonCard(n: Int, lesson: LessonDef, done: Boolean, onStart: () -> Unit, onFreePlay: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(p.surface).border(1.dp, if (done) p.good.copy(alpha = 0.5f) else p.line, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) p.good else p.sunk)
                    .semantics { contentDescription = if (done) "Lesson $n, finished" else "Lesson $n" },
                contentAlignment = Alignment.Center,
            ) { Text(if (done) "✓" else "$n", color = if (done) p.surface else p.muted, fontWeight = FontWeight.Bold, fontFamily = Mono) }
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, Modifier.weight(1f).semantics { contentDescription = "${if (done) "Review" else "Start"} lesson: ${lesson.title}" }) {
                Text(if (done) "Review" else "Start")
            }
            OutlinedButton(onClick = onFreePlay, Modifier.weight(1f).semantics { contentDescription = "Free play: ${lesson.title}" }) { Text("Free play") }
        }
    }
}

/** Choose a level for free play. Levels above the one reached in practice are locked. */
@Composable
fun LevelPicker(title: String, unlocked: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick a level. Free play is graded but doesn't change your course level.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
                for (level in 1..5) {
                    val open = level <= unlocked
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (open) p.accentSoft else p.sunk)
                            .clickable(enabled = open) { onPick(level) }
                            .alpha(if (open) 1f else 0.55f)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Level $level", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (open) p.ink else p.muted)
                        Text(
                            if (open) listOf("", "Short", "Medium", "Medium", "Long", "Longest")[level] else "Reach it in Practice",
                            style = MaterialTheme.typography.bodySmall, color = p.muted,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
