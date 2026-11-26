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

/** Truth trees, possible worlds, timelines and constructive logic, played with real taps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class NewLogicFlowTest {
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

    private fun openLesson(course: String, lesson: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(course))
        tap(course)
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Start lesson: $lesson"))
        tapDesc("Start lesson: $lesson")
    }

    private fun leave() {
        tapDesc("Close lesson")
        waitFor("Lessons")
        tapDesc("Back to courses")
        rule.waitForIdle()
    }

    private fun on(desc: String) = rule.onNode(hasContentDescription(desc)).performScrollTo().performClick()

    @Test fun newKindsOfLogic() {
        waitFor("Courses")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Constructive Logic"))
        shot("N0-learn-sections")

        // Truth trees: a valid argument, then an invalid one.
        openLesson("Truth Trees", "How a tree works")
        waitFor("Looking for a counterexample")
        tap("Continue")
        waitFor("Your first tree")
        on("Tree line 1: P → Q")
        press("Stack")
        waitFor("Not quite")
        shot("N1-tree-wrong-shape")
        press("Branch")
        on("Close the branch ending at line 4")
        on("Close the branch ending at line 5")
        waitFor("So is the argument valid?")
        shot("N2-tree-closed")
        press("Valid")
        waitFor("Both branches closed")
        tap("Continue")
        waitFor("A tree that stays open")
        on("Tree line 1: P → Q")
        press("Branch")
        waitFor("So is the argument valid?")
        press("Invalid")
        waitFor("The open branch gives the counterexample")
        shot("N3-tree-open")
        leave()

        // Possible worlds.
        openLesson("Possible Worlds", "Necessarily and possibly")
        waitFor("Ways things could be")
        tap("Continue")
        waitFor("w1 sees w2 and w3")
        shot("N4-worlds-start")
        on("P at w2, off"); on("P at w3, off")
        waitFor("P is true in every world w1 sees")
        shot("N5-worlds-done")
        leave()

        // Time.
        openLesson("Logic of Time", "Always and eventually")
        waitFor("The same symbols, read in time")
        tap("Continue")
        for (t in 0..4) on("P at moment $t, off")
        waitFor("P at every moment from now on")
        shot("N6-timeline")
        leave()

        // Constructive logic: excluded middle fails.
        openLesson("Constructive Logic", "States of knowledge")
        waitFor("Knowledge that grows")
        tap("Continue")
        on("P at w2, off")
        waitFor("Neither side holds now")
        shot("N7-constructive")
        tapDesc("Close lesson")
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("New practice set"))
        press("New practice set")
        waitFor("Is ")
        shot("N8-practice-set")
    }
}
