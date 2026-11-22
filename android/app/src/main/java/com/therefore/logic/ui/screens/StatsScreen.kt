@file:OptIn(ExperimentalMaterial3Api::class)

package com.therefore.logic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.data.LearningStats
import com.therefore.logic.data.Progress
import com.therefore.logic.ui.components.ColumnChart
import com.therefore.logic.ui.components.Panel
import com.therefore.logic.ui.components.RankedBars
import com.therefore.logic.ui.components.TrendLine
import com.therefore.logic.ui.theme.LocalPalette
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(stats: LearningStats, progress: Progress, onBack: () -> Unit) {
    val p = LocalPalette.current
    val day = DateTimeFormatter.ofPattern("MMM d")
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Your stats") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Headline numbers: these are single values, so tiles rather than charts.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val time = stats.averageSeconds.let { if (it >= 60) "${it / 60}m ${it % 60}s" else "${it}s" }
                    for ((v, l) in listOf("${stats.totalSolved}" to "proofs solved", "${(stats.perfectShare * 100).toInt()}%" to "perfect proofs", time to "average time")) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(12.dp)) {
                            Text(v, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(l, style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((v, l) in listOf("${progress.streak}" to "day streak", "${progress.bestStreak}" to "best streak", "${stats.lessonsDone}" to "lessons finished")) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(12.dp)) {
                            Text(v, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(l, style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                    }
                }
            }
            item {
                ChartCard("Proofs solved per day", "Last 30 days") {
                    ColumnChart(
                        stats.perDay.map { it.second.toFloat() },
                        stats.perDay.map { it.first.format(day) },
                        describe = { i -> "${stats.perDay[i].first.format(day)}: ${stats.perDay[i].second} solved" },
                        summary = "Proofs solved per day over the last 30 days: " + stats.perDay.joinToString { "${it.first.format(day)} ${it.second}" },
                        axisLabels = listOf(stats.perDay.first().first.format(day), stats.perDay[stats.perDay.size / 2].first.format(day), "Today"),
                    )
                }
            }
            item {
                ChartCard("Grades", "Every puzzle you've solved") {
                    ColumnChart(
                        stats.grades.map { it.second.toFloat() },
                        stats.grades.map { it.first },
                        describe = { i -> "Grade ${stats.grades[i].first}: ${stats.grades[i].second}" },
                        summary = "Grades: " + stats.grades.joinToString { "${it.first} ${it.second}" },
                    )
                }
            }
            item {
                ChartCard("Mistakes per proof", "Weekly average, last 8 weeks. Lower is better.") {
                    TrendLine(
                        stats.mistakesByWeek.map { it.second },
                        stats.mistakesByWeek.mapIndexed { i, w -> if (i % 2 == 0) w.first.format(day) else "" },
                        describe = { i -> "Week of ${stats.mistakesByWeek[i].first.format(day)}: ${String.format("%.1f", stats.mistakesByWeek[i].second ?: 0f)} mistakes per proof" },
                        summary = "Mistakes per proof by week: " + stats.mistakesByWeek.joinToString { "${it.first.format(day)} ${it.second?.let { v -> String.format("%.1f", v) } ?: "none"}" },
                    )
                }
            }
            item {
                ChartCard("Trickiest rules", "The rule you picked for each rejected line. Counting starts with this update.") {
                    RankedBars(stats.trickyRules, "Trickiest rules: " + stats.trickyRules.joinToString { "${it.first} ${it.second}" }, empty = "No mistakes recorded yet. Nice.")
                }
            }
            item {
                ChartCard("Most-used rules", "Rules in lines you've written") {
                    RankedBars(stats.favouriteRules, "Most-used rules: " + stats.favouriteRules.joinToString { "${it.first} ${it.second}" }, empty = "Solve a puzzle to start counting.")
                }
            }
            item {
                ChartCard("Practice by course", "Practice puzzles solved") {
                    val active = stats.courses.filter { it.second > 0 }.sortedByDescending { it.second }
                    RankedBars(active, "Practice by course: " + active.joinToString { "${it.first} ${it.second}" }, empty = "No practice puzzles solved yet.")
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            content()
        }
    }
}
