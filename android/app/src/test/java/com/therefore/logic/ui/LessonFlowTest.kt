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

/** Walks the tutorial lesson with real taps, following each coach instruction. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class LessonFlowTest {
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

    @Test fun tutorialLessonThenFreePlay() {
        waitFor("Courses")
        tap("If–Then Basics")
        waitFor("Your first proof")
        shot("L1-course-page")
        tap("Start")

        // Step 1: reading. Step 2: quiz.
        waitFor("What a proof is")
        shot("L2-read")
        tap("Continue")
        waitFor("which line is the conclusion")
        tap("P")             // wrong answer first
        waitFor("Not quite")
        shot("L3-quiz-wrong")
        tap("Try again")
        rule.onAllNodesWithText("Q").onFirst().performClick()
        waitFor("Correct")
        tap("Continue")

        // Step 3: the guided first proof, one coach instruction at a time.
        waitFor("Start by tapping line 1")
        shot("L4-guided-start")
        cite(1)
        waitFor("Now tap line 2")
        cite(2)
        waitFor("tap MP")
        tap("MP")
        waitFor("tap Q")
        tapDesc("Q")
        waitFor("Tap Add line")
        shot("L5-guided-before-add")
        tapDesc("Add line")
        waitFor("Proof complete")
        shot("L6-guided-done")
        tap("Continue")

        // Step 4: mistakes, undo and hints.
        waitFor("make one on purpose")
        cite(1); tap("MP"); tapDesc("Q"); tapDesc("Add line")
        waitFor("also cite line 3")
        shot("L7-mistake")
        cite(3); tapDesc("Add line")
        waitFor("Tap Undo")
        tapDesc("Remove last line")
        waitFor("Now tap Hint")
        tap("Hint")
        waitFor("finish the proof of R")
        repeat(3) { tap("Hint") }
        tap("Fill it in"); tapDesc("Add line")
        repeat(3) { tap("Hint") }
        tap("Fill it in"); tapDesc("Add line")
        waitFor("Hints lower your grade")
        tap("Continue")

        // Step 5: reading, then finish.
        waitFor("Handy tools")
        tap("Finish lesson")
        waitFor("Free play this lesson")
        shot("L8-complete")

        // Free play at a chosen level.
        tap("Free play this lesson")
        waitFor("Level 5")
        shot("L9-level-picker")
        tap("Level 1")
        waitFor("Free play · Level 1")
        shot("L10-free-play")
        tapDesc("Leave puzzle")
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Your first proof"))
        // Finished lessons are saved on a background thread; give it a moment.
        repeat(20) {
            if (rule.onAllNodes(hasContentDescription("Lesson 1, finished")).fetchSemanticsNodes().isNotEmpty()) return@repeat
            Thread.sleep(200); rule.waitForIdle()
        }
        shot("L11-course-after")
        rule.waitUntil(3000) { rule.onAllNodes(hasContentDescription("Lesson 1, finished")).fetchSemanticsNodes().isNotEmpty() }
        shot("L11-course-after")
    }
}
