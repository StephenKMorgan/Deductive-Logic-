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

data class DayResult(val grade: String, val stars: Int) {
    val studied get() = grade == Grades.REVEALED
}

data class Progress(
    val courses: Map<String, CourseStats> = emptyMap(),
    val days: Map<String, DayResult> = emptyMap(),
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
    /** Daily proof only: no mistakes, no hints, at or under par. */
    const val S = "S"
    private val rank = mapOf(S to 5, "A" to 4, "B" to 3, "C" to 2, "D" to 1)
    fun better(a: String, b: String?): String = if (b == null || (rank[a] ?: 0) > (rank[b] ?: 0)) a else b
}

class ProgressRepository(private val dao: AttemptDao, private val lessons: LessonDao) {
    val progress: Flow<Progress> = combine(dao.all(), lessons.done()) { attempts, done ->
        compute(attempts, LocalDate.now()).copy(lessonsDone = done.toSet())
    }

    suspend fun current(): Progress = progress.first()

    companion object {
        fun compute(attempts: List<Attempt>, today: LocalDate): Progress {
            val courses = HashMap<String, CourseStats>()
            val days = HashMap<String, DayResult>()
            for (a in attempts) {
                if (a.courseId == Curriculum.DAILY_ID) {
                    val prev = days[a.date]
                    days[a.date] = when {
                        prev == null -> DayResult(a.grade, a.stars)
                        a.revealed -> prev
                        prev.studied -> DayResult(a.grade, a.stars)
                        else -> DayResult(Grades.better(a.grade, prev.grade), maxOf(a.stars, prev.stars))
                    }
                } else if (!a.revealed && a.mode == Mode.PRACTICE.name) {
                    val prev = courses[a.courseId] ?: CourseStats()
                    courses[a.courseId] = CourseStats(prev.solved + 1, Grades.better(a.grade, prev.best))
                }
            }
            fun solvedOn(d: LocalDate) = days[d.toString()]?.let { !it.studied } == true
            var day = if (solvedOn(today)) today else today.minusDays(1)
            var streak = 0
            while (solvedOn(day)) { streak++; day = day.minusDays(1) }
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            for (d in days.filterValues { !it.studied }.keys.map(LocalDate::parse).sorted()) {
                run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            return Progress(
                courses = courses,
                days = days,
                streak = streak,
                bestStreak = best,
                totalSolved = attempts.count { !it.revealed },
                perfect = attempts.count { !it.revealed && it.mistakes == 0 && it.hints == 0 },
            )
        }
    }
}
