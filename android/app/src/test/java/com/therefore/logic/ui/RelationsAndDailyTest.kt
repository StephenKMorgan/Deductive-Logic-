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

/** The relations world explorer, the Daily tab with both daily proofs, and the daily predicate puzzle. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class RelationsAndDailyTest {
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

    @Test fun relationsAndDailyPredicate() {
        waitFor("Courses")
        tap("Predicate")
        waitFor("Predicate Logic")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Relations"))
        shot("R1-tab")
        tap("Relations")
        waitFor("Lessons")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Start lesson: Two-place predicates"))
        tapDesc("Start lesson: Two-place predicates")
        waitFor("Relations"); tap("Continue")
        waitFor("Make Lab true but Lba false")
        rule.onNode(hasContentDescription("Lab is off", substring = true)).performClick()
        waitFor("doesn't mean b loves a")
        shot("R2-relation-grid")
        tap("Continue")
        waitFor("every row needs at least one square")
        for (cell in listOf("Lab", "Lbc", "Lca")) rule.onNode(hasContentDescription("$cell is off", substring = true)).performClick()
        waitFor("though not necessarily the same thing")
        shot("R3-everyone-loves-someone")
        tapDesc("Close lesson")

        // Practice puzzle with relations.
        waitFor("Lessons")
        tap("Practice · Level 1")
        waitFor("Hint")
        shot("R4-relations-practice")
        tapDesc("Leave puzzle")
        waitFor("Lessons")
        tapDesc("Back to courses")

        // The Daily tab now has two daily proofs.
        tap("Daily")
        waitFor("Daily predicate proof")
        shot("R5-daily-tab")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Daily predicate proof"))
        rule.onAllNodesWithText("Start")[1].performClick()
        waitFor("Par")
        shot("R6-daily-predicate")
    }
}
