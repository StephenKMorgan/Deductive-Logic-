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
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.therefore.logic.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Games tab: each game played with real taps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class GamesFlowTest {
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

    @Test fun playEachGame() {
        waitFor("Courses")
        tap("Games")
        waitFor("Logic Games")
        shot("G1-hub")

        // Truth-tellers & Liars: a wrong answer first, then the right one.
        tap("Truth-tellers & Liars")
        rule.waitUntil(5000) { games.island.value != null }
        rule.waitForIdle()
        val puzzle = games.island.value!!.puzzle
        puzzle.names.forEachIndexed { i, n -> tapDesc("$n: ${if (!puzzle.solution[i] xor (i == 0)) "Liar" else "Truth-teller"}") }
        tap("Check")
        waitFor("Not quite")
        shot("G2-island-wrong")
        tapDesc("${puzzle.names[0]}: ${if (puzzle.solution[0]) "Truth-teller" else "Liar"}")
        tap("Check")
        waitFor("Solved!")
        shot("G3-island-solved")
        tapDesc("Back to games")

        // Valid or Not?: three right answers and one wrong.
        waitFor("Logic Games")
        tap("Valid or Not?")
        waitFor("Best score")
        tap("Start")
        rule.waitUntil(3000) { games.quiz.value.question != null }
        repeat(4) { k ->
            val q = games.quiz.value.question!!
            val pickYes = if (k < 3) q.answer else !q.answer
            val (yes, no) = if (q is com.therefore.logic.games.Question.Argument) "Valid" to "Invalid" else "Same" to "Different"
            tap(if (pickYes) yes else no)
            rule.waitForIdle()
        }
        waitFor("Last answer: wrong")
        shot("G4-quiz")
        tapDesc("Back to games")

        // Switchboard: flip to the solution.
        waitFor("Logic Games")
        tap("Switchboard")
        rule.waitUntil(5000) { games.board.value != null }
        rule.waitForIdle()
        shot("G5-board")
        games.board.value!!.board.solution.filterValues { it }.keys.forEach { tapDesc("Switch $it, false") }
        waitFor("All lit")
        shot("G6-board-solved")
    }
}
