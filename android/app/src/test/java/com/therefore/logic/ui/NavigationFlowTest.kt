package com.therefore.logic.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.therefore.logic.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Checks the rule dropdown, the keypad and every tab. Saves screenshots to build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class NavigationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Draw the window directly; Robolectric doesn't produce hardware frames for captureToImage().
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) = rule.onNodeWithText(text).performClick()
    private fun tapDesc(desc: String) = rule.onNodeWithContentDescription(desc).performClick()
    private fun waitFor(text: String, timeout: Long = 5000) = try {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        shot("fail-" + text.filter { it.isLetterOrDigit() })
        throw e
    }

    @Test fun ruleDropdownAndTabs() {
        waitFor("Courses")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Grandmaster"))
        tap("Grandmaster")
        waitFor("Lessons")
        tap("Practice · Level 1")
        waitFor("Hint")
        shot("7a-grandmaster")
        tapDesc("Rule picker")
        waitFor("Exportation")
        shot("7-dropdown-open")
        // Robolectric can't route touches into popup windows, so trigger the item's click action directly.
        rule.onNode(hasText("Exportation") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Exp · Exportation")
        waitFor("Exportation · cite one line")
        // Write "P → Q" with the keypad and move the cursor by tapping the first symbol.
        tapDesc("P"); tapDesc("if then"); tapDesc("Q")
        shot("8-dropdown-selected")
        tapDesc("Leave puzzle")
        waitFor("Resume practice puzzle")
        tapDesc("Back to courses")
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        waitFor("Resume")

        tap("Daily")
        waitFor("day streak")
        shot("9-daily")
        tap("Rules")
        waitFor("Rules of inference")
        shot("10-rules")
        tap("Learn")
        tapDesc("Settings")
        waitFor("Notation")
        shot("11-settings")
    }

    private fun tapDesc(desc: String, substring: Boolean): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(desc, substring = substring)).performClick()
}
