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

/** The daily calendar with catch-up play, color themes, and the stats screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class CalendarStatsThemeTest {
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

    private val container get() = (rule.activity.application as com.therefore.logic.TheApp).container

    /** A few weeks of history so the calendar and charts have something to show. */
    private fun seed() = kotlinx.coroutines.runBlocking {
        val today = java.time.LocalDate.now()
        val r = kotlin.random.Random(7)
        for (d in 1..24) {
            val date = today.minusDays(d.toLong())
            val at = date.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (d % 5 != 0) container.db.attempts().insert(com.therefore.logic.data.Attempt(
                courseId = "daily", level = 3, date = date.toString(), grade = if (d % 9 == 0) "S" else listOf("A", "A", "B", "C")[r.nextInt(4)],
                stars = 3, score = 90, lines = 5, modelLines = 5, mistakes = r.nextInt(3), hints = 0, seconds = 80L + r.nextInt(100),
                revealed = false, finishedAt = at, mode = "DAILY", rulesUsed = "MP,MT,DS,Simp", mistakeRules = listOf("", "DS", "MT,DS", "Simp")[r.nextInt(4)],
            ))
            if (d % 3 == 0) container.db.attempts().insert(com.therefore.logic.data.Attempt(
                courseId = "daily-pred", level = 3, date = date.toString(), grade = "A", stars = 3, score = 92, lines = 5, modelLines = 5,
                mistakes = 0, hints = 0, seconds = 120, revealed = false, finishedAt = at, mode = "DAILY", rulesUsed = "UI,MP,EG",
            ))
        }
    }

    @Test fun calendarThemesAndStats() {
        waitFor("Courses")
        seed()
        tap("Daily")
        waitFor("Daily predicate proof")
        Thread.sleep(300); rule.waitForIdle()
        shot("Q1-calendar")

        // Catch up on yesterday's daily predicate proof.
        val yesterday = java.time.LocalDate.now().minusDays(1)
        rule.onNode(hasContentDescription(yesterday.toString(), substring = true)).performClick()
        waitFor("don't count toward your streak")
        shot("Q2-past-day")

        // Stats, from the home screen.
        tap("Learn")
        waitFor("Courses")
        tap("Stats")
        waitFor("Proofs solved per day")
        Thread.sleep(300); rule.waitForIdle()
        shot("Q3-stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Trickiest rules"))
        shot("Q4-stats-rules")
        tapDesc("Back")

        // A new color.
        tapDesc("Settings")
        waitFor("Color")
        tapDesc("Teal color")
        Thread.sleep(300); rule.waitForIdle()
        shot("Q5-settings-teal")
        tapDesc("Back")
        waitFor("Courses")
        shot("Q6-home-teal")
    }
}
