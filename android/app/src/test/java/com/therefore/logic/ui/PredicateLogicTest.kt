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

/** The Predicate tab: the world explorer, the Socrates proof on the predicate keypad, and a practice puzzle. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PredicateLogicTest {
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

    @Test fun predicateTab() {
        waitFor("Courses")
        tap("Predicate")
        waitFor("Predicate Logic")
        shot("P1-tab")
        tap("Quantifiers")
        waitFor("Lessons")
        shot("P2-course")
        rule.onAllNodesWithText("Start")[0].performClick()

        // Two reading steps, then the world explorer.
        waitFor("Looking inside statements"); tap("Continue")
        waitFor("All and some"); tap("Continue")
        waitFor("Make ∀x Fx true")
        repeat(3) { i -> rule.onNode(hasContentDescription("${"abc"[i]} is not F", substring = true)).performClick() }
        waitFor("only true when every single thing")
        shot("P3-world-all")
        tap("Continue")
        waitFor("but ∀x Fx false")
        rule.onNode(hasContentDescription("a is not F", substring = true)).performClick()
        waitFor("not everything is")
        shot("P4-world-some")
        tapDesc("Close lesson")

        // Lesson 2: Socrates, typed on the predicate keypad.
        waitFor("Lessons")
        rule.onAllNodesWithText("Start")[1].performClick()
        waitFor("Dropping ∀"); tap("Continue")
        waitFor("Cite it, choose UI")
        cite(1); tap("UI")
        tapDesc("predicate H"); tapDesc("name s"); tapDesc("if then"); tapDesc("predicate M"); tapDesc("name s")
        shot("P5-socrates-typed")
        tapDesc("Add line")
        waitFor("Modus Ponens with line 2")
        cite(2); cite(3); tap("MP"); tapDesc("predicate M"); tapDesc("name s"); tapDesc("Add line")
        waitFor("Socrates is mortal")
        shot("P6-socrates-done")
        tapDesc("Close lesson")

        // A generated practice puzzle.
        waitFor("Lessons")
        tap("Practice · Level 1")
        waitFor("Hint")
        shot("P7-practice")
    }
}
