package com.therefore.logic.data

import com.therefore.logic.content.Curriculum
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything the stats screen charts, worked out from the history of finished puzzles. */
data class LearningStats(
    val totalSolved: Int,
    val perfectShare: Float,
    val averageSeconds: Long,
    val lessonsDone: Int,
    /** Proofs solved on each of the last 30 days, oldest first. */
    val perDay: List<Pair<LocalDate, Int>>,
    /** S, A, B, C, D. */
    val grades: List<Pair<String, Int>>,
    /** Average mistakes per solved proof, for each of the last 8 weeks (null when nothing was solved that week). */
    val mistakesByWeek: List<Pair<LocalDate, Float?>>,
    /** Rules picked for rejected lines, most often first. */
    val trickyRules: List<Pair<String, Int>>,
    /** Rules used in accepted lines, most often first. */
    val favouriteRules: List<Pair<String, Int>>,
    /** Practice puzzles solved in each course. */
    val courses: List<Pair<String, Int>>,
)

object Stats {
    fun compute(attempts: List<Attempt>, progress: Progress, today: LocalDate): LearningStats {
        val solved = attempts.filter { !it.revealed }
        val byDay = solved.groupingBy { ProgressRepository.finishedOn(it) }.eachCount()
        val perDay = (29 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (byDay[it.toString()] ?: 0) }
        val grades = listOf(Grades.S, "A", "B", "C", "D").map { g -> g to solved.count { it.grade == g } }
        val weekStart = today.minusDays(7L * 7 + today.dayOfWeek.value - 1)
        val mistakesByWeek = (0 until 8).map { w ->
            val start = weekStart.plusWeeks(w.toLong())
            val inWeek = solved.filter {
                val d = LocalDate.parse(ProgressRepository.finishedOn(it))
                !d.isBefore(start) && ChronoUnit.DAYS.between(start, d) < 7
            }
            start to if (inWeek.isEmpty()) null else inWeek.sumOf { it.mistakes }.toFloat() / inWeek.size
        }
        fun tally(field: (Attempt) -> String) = attempts.flatMap { field(it).split(",") }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val courses = Curriculum.allCourses.map { it.title to progress.course(it.id).solved }
        return LearningStats(
            totalSolved = solved.size,
            perfectShare = if (solved.isEmpty()) 0f else solved.count { it.mistakes == 0 && it.hints == 0 }.toFloat() / solved.size,
            averageSeconds = if (solved.isEmpty()) 0 else solved.sumOf { it.seconds } / solved.size,
            lessonsDone = progress.lessonsDone.size,
            perDay = perDay,
            grades = grades,
            mistakesByWeek = mistakesByWeek,
            trickyRules = tally { it.mistakeRules }.take(6),
            favouriteRules = tally { it.rulesUsed }.take(8),
            courses = courses,
        )
    }
}
