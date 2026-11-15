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

/** Walks the "Proving a positive" lesson: AIP fills in ¬P, the clash turns red, IP closes it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class IndirectProofLessonTest {
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

    @Test fun contradictionTurnsRed() {
        waitFor("Courses")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Indirect Proof"))
        tap("Indirect Proof")
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Proving a positive"))
        // The third lesson's Start button.
        rule.onAllNodesWithText("Start")[2].performClick()
        waitFor("Assume the opposite")
        tap("Continue")
        waitFor("it fills in ¬P")
        tap("AIP")
        waitFor("Filled in ¬P")
        shot("I1-aip-filled")
        tapDesc("Add line")
        waitFor("Use MP on lines 1 and 3")
        cite(1); cite(3); tap("MP"); tapDesc("Q"); tapDesc("Add line")
        waitFor("Contradiction!")
        shot("I2-red-contradiction")
        rule.waitUntil(3000) { rule.onAllNodes(hasContentDescription("contradiction", substring = true)).fetchSemanticsNodes().size == 2 }
        cite(2); cite(4); tap("Conj"); tapDesc("Q"); tapDesc("and"); tapDesc("not"); tapDesc("Q"); tapDesc("Add line")
        waitFor("Close with IP")
        tap("IP")
        waitFor("Filled in P")
        tapDesc("Add line")
        waitFor("so P.")
        shot("I3-done")
    }
}
