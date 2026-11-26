package com.therefore.logic.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.therefore.logic.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The SAT course: the solver lesson played with real taps, and the Solver game. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SatCourseTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String, timeout: Long = 5000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        shot("fail-lesson-" + text.filter { it.isLetterOrDigit() }.take(30))
        throw e
    }

    private fun tap(text: String) = rule.onAllNodesWithText(text).onFirst().performClick()
    private fun tapDesc(desc: String) = rule.onNodeWithContentDescription(desc).performClick()
    private fun cite(n: Int) = rule.onNode(hasContentDescription("Line $n,", substring = true)).performClick()
    private fun pickRule(title: String) {
        tapDesc("Rule picker")
        waitFor(title)
        rule.onNode(hasText(title) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private val games get() = androidx.lifecycle.ViewModelProvider(rule.activity)[GamesViewModel::class.java]

    private fun press(text: String) = rule.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun force(i: Int, lit: String) = rule.onNode(hasContentDescription("Force clause $i: $lit")).performScrollTo().performClick()
    private fun guess(letter: Char, value: Boolean) {
        rule.onNode(hasContentDescription("Letter $letter, unset")).performScrollTo().performClick()
        val item = "Guess $letter is ${if (value) "true" else "false"}"
        waitFor(item)
        rule.onNode(hasText(item) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    @Test fun solverLessonAndGame() {
        waitFor("Courses")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("How Computers Solve Logic"))
        shot("S1-learn-section")
        tap("How Computers Solve Logic")
        waitFor("Lessons")
        shot("S2-course")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Start lesson: Solving like a computer"))
        tapDesc("Start lesson: Solving like a computer")
        waitFor("Forced moves")
        tap("Continue")

        // Forced moves only.
        waitFor("Allowed guesses: none")
        shot("S3-forced-start")
        force(1, "P"); force(2, "Q"); force(3, "R"); force(4, "S")
        waitFor("Solved without a single guess")
        shot("S4-forced-done")
        tap("Continue")
        waitFor("Guess, then propagate")
        tap("Continue")

        // One guess, then propagate.
        waitFor("Allowed guesses: 1")
        guess('P', true)
        press("Propagate")
        waitFor("One guess and the rest followed")
        shot("S5-one-guess")
        tap("Continue")

        // A conflict, then backtracking.
        waitFor("Hitting a conflict")
        guess('P', true)
        press("Propagate")
        waitFor("a conflict. Some guess was wrong")
        shot("S6-conflict")
        press("Backtrack")
        press("Propagate")
        waitFor("P true forced both Q and ¬Q")
        tap("Continue")

        // Proving there's no answer.
        waitFor("Proving it can't be done")
        guess('P', true)
        press("Propagate"); press("Backtrack"); press("Propagate"); press("Backtrack")
        waitFor("Every branch ended in a conflict, so")
        shot("S7-unsat")
        tap("Continue")
        waitFor("Where solvers are used")
        tap("Finish lesson")
        waitFor("Back to the course", 8000)
        shot("S7b-lesson-complete")
        tap("Back to the course")
        waitFor("Lessons")

        // The Solver game, played to the end through the view model.
        tapDesc("Back to courses")
        waitFor("How computers do logic")
        tap("Games")
        waitFor("Logic Games")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Solver"))
        tap("Solver")
        rule.waitUntil(8000) { games.solver.value != null }
        rule.waitForIdle()
        shot("S8-solver-game")
        var n = 0
        while (!games.solver.value!!.state.finished && n++ < 200) {
            val s = games.solver.value!!.state
            when {
                s.conflict != null -> games.solverBacktrack()
                s.units.isNotEmpty() -> games.solverPropagate()
                else -> games.solverGuess(s.cnf.letters.indices.first { it !in s.values }, true)
            }
        }
        waitFor("Next board")
        shot("S9-solver-done")
    }
}
