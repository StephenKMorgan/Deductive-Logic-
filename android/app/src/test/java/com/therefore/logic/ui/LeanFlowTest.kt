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

/** The Lean course (an order step) and the Lean view of a finished proof. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class LeanFlowTest {
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

    private val game get() = androidx.lifecycle.ViewModelProvider(rule.activity)[GameViewModel::class.java]

    @Test fun leanLessonAndProofView() {
        waitFor("Courses")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Everyday Arguments"))
        shot("L0-learn-later-sections")
        openLesson("Your Proofs in Lean", "Meet Lean")
        waitFor("Proofs a computer can check")
        shot("L1-lean-read")
        tap("Continue")
        waitFor("which Lean term proves Q")
        tap("h1 h2")
        tap("Continue")
        waitFor("Your rules in Lean")
        tap("Continue")
        waitFor("Assemble this Lean proof")
        on("Piece:   have h4 : Q := h1 h3")
        waitFor("Not next")
        shot("L2-order-wrong")
        on("Piece: example (P Q R : Prop) (h1 : P → Q) (h2 : Q → R) (h3 : P) : R := by")
        on("Piece:   have h4 : Q := h1 h3")
        on("Piece:   have h5 : R := h2 h4")
        on("Piece:   exact h5")
        waitFor("Exactly the proof you'd write")
        shot("L3-order-done")
        leave()

        // Solve a course 1 practice puzzle with hints, then open its Lean view.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("If–Then Basics"))
        tap("If–Then Basics")
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Practice · Level 1"))
        tap("Practice · Level 1")
        rule.waitUntil(8000) { game.state.value?.done == false }
        var n = 0
        while (game.state.value?.done != true && n++ < 30) {
            rule.runOnUiThread { repeat(3) { game.hint() }; game.fillHint(); game.addLine() }
            rule.waitForIdle()
        }
        waitFor("Model proof, explained", 8000)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Your proof in Lean"))
        tap("Your proof in Lean")
        waitFor("import Mathlib.Tactic")
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Open in Lean's web editor"))
        shot("L4-proof-in-lean")
    }
}
