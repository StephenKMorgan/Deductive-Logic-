package com.therefore.logic.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
 * Drives the real app the way a person would: opens a course, makes a mistake, solves the
 * puzzle with hints, and checks the graded results. Saves screenshots to build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class AppFlowTest {
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

    @Test fun learnSolveAndGrade() {
        waitFor("Courses")
        shot("1-home")

        tap("If–Then Basics")
        waitFor("Worked example")
        shot("2-lesson")
        rule.onAllNodesWithText("Start first puzzle").onFirst().performClick()
        waitFor("Hint")

        // A deliberate mistake: cite line 1, pick MP, write "S ∧ S" (never a valid MP result here).
        tapDesc("Line 1", substring = true)
        tap("MP")
        tapDesc("S"); tapDesc("and"); tapDesc("S")
        tapDesc("Add line")
        waitFor("Modus Ponens")
        shot("3-play-mistake")

        // Solve with hints: three hint levels, then "Fill it in" and add the line.
        var guard = 0
        while (rule.onAllNodesWithText("Model proof, explained").fetchSemanticsNodes().isEmpty() && guard++ < 12) {
            if (rule.onAllNodesWithText("Hint").fetchSemanticsNodes().isEmpty()) break
            repeat(3) { tap("Hint") }
            if (guard == 1) shot("4-play-hint")
            tap("Fill it in")
            tapDesc("Add line")
            rule.waitForIdle()
        }
        waitFor("Model proof, explained")
        shot("5-result")
        assertTrue(rule.onAllNodesWithText("mistakes").fetchSemanticsNodes().isNotEmpty())
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Model proof, explained"))
        shot("6-result-model-proof")

        // The level and grade show up on the course list.
        tapDesc("Done")
        waitFor("Courses")
        val db = (rule.activity.application as com.therefore.logic.TheApp).container.db
        // The attempt is saved on a background thread; give it a moment.
        for (t in 0 until 10) {
            val saved = kotlinx.coroutines.runBlocking { db.attempts().all().first() }
            println("t=${t * 300}ms attempts in database: ${saved.size} ${saved.map { it.courseId + ":" + it.grade }}")
            if (saved.isNotEmpty()) break
            Thread.sleep(300); rule.waitForIdle()
        }
        waitFor("1 solved", 10_000)
    }

    private fun tapDesc(desc: String, substring: Boolean): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(desc, substring = substring)).performClick()
}
