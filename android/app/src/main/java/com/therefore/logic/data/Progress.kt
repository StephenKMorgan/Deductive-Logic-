package com.therefore.logic.data

import com.therefore.logic.content.Curriculum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class CourseStats(val solved: Int = 0, val best: String? = null) {
    /** Puzzles get longer every two solves, up to level 5. */
    val level: Int get() = minOf(5, 1 + solved / 2)
}

/** [onTime]: solved on the puzzle's own day (only these count toward the streak). */
data class DayResult(val grade: String, val stars: Int, val onTime: Boolean = true) {
    val studied get() = grade == Grades.REVEALED
}

data class Progress(
    val courses: Map<String, CourseStats> = emptyMap(),
    val days: Map<String, DayResult> = emptyMap(),
    /** The daily predicate proof, by date. */
    val predicateDays: Map<String, DayResult> = emptyMap(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val totalSolved: Int = 0,
    val perfect: Int = 0,
    val lessonsDone: Set<String> = emptySet(),
) {
    fun course(id: String) = courses[id] ?: CourseStats()
}

object Grades {
    const val REVEALED = "–"
    /** Daily proof only: no mistakes, no hints, and fewer lines than par. */
    const val S = "S"
    private val rank = mapOf(S to 5, "A" to 4, "B" to 3, "C" to 2, "D" to 1)
    fun better(a: String, b: String?): String = if (b == null || (rank[a] ?: 0) > (rank[b] ?: 0)) a else b
}

class ProgressRepository(private val dao: AttemptDao, private val lessons: LessonDao) {
    val progress: Flow<Progress> = combine(dao.all(), lessons.done()) { attempts, done ->
        compute(attempts, LocalDate.now()).copy(lessonsDone = done.toSet())
    }

    suspend fun current(): Progress = progress.first()

    /** Every finished puzzle, oldest first (for the stats screen). */
    val attempts: Flow<List<Attempt>> = dao.all()

    companion object {
        /** The local date an attempt was finished on. */
        fun finishedOn(a: Attempt): String =
            java.time.Instant.ofEpochMilli(a.finishedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

        fun compute(attempts: List<Attempt>, today: LocalDate): Progress {
            val courses = HashMap<String, CourseStats>()
            val days = HashMap<String, DayResult>()
            val predicateDays = HashMap<String, DayResult>()
            for (a in attempts) {
                if (Curriculum.isDaily(a.courseId)) {
                    val map = if (a.courseId == Curriculum.DAILY_ID) days else predicateDays
                    val prev = map[a.date]
                    val onTime = !a.revealed && finishedOn(a) == a.date
                    map[a.date] = when {
                        prev == null -> DayResult(a.grade, a.stars, onTime)
                        a.revealed -> prev
                        prev.studied -> DayResult(a.grade, a.stars, onTime)
                        else -> DayResult(Grades.better(a.grade, prev.grade), maxOf(a.stars, prev.stars), prev.onTime || onTime)
                    }
                } else if (!a.revealed && a.mode == Mode.PRACTICE.name) {
                    val prev = courses[a.courseId] ?: CourseStats()
                    courses[a.courseId] = CourseStats(prev.solved + 1, Grades.better(a.grade, prev.best))
                }
            }
            // A day counts toward the streak if either daily proof was solved.
            // A day counts toward the streak if either daily proof was solved on that day.
            fun solvedOn(d: LocalDate) = listOf(days, predicateDays).any { m -> m[d.toString()]?.let { !it.studied && it.onTime } == true }
            var day = if (solvedOn(today)) today else today.minusDays(1)
            var streak = 0
            while (solvedOn(day)) { streak++; day = day.minusDays(1) }
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            val solvedDays = (days.filterValues { !it.studied && it.onTime }.keys + predicateDays.filterValues { !it.studied && it.onTime }.keys).toSet()
            for (d in solvedDays.map(LocalDate::parse).sorted()) {
                run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            return Progress(
                courses = courses,
                days = days,
                predicateDays = predicateDays,
                streak = streak,
                bestStreak = best,
                totalSolved = attempts.count { !it.revealed },
                perfect = attempts.count { !it.revealed && it.mistakes == 0 && it.hints == 0 },
            )
        }
    }
}
