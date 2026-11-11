package com.therefore.logic.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.therefore.logic.TheApp
import com.therefore.logic.data.GameState
import com.therefore.logic.engine.Proofs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/** The game logic without the UI: solving a puzzle must record an attempt and raise the level. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<TheApp>()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> waitFor(timeoutMs: Long = 5000, read: () -> T, ok: (T) -> Boolean): T {
        val end = System.currentTimeMillis() + timeoutMs
        var v = read()
        while (!ok(v) && System.currentTimeMillis() < end) { idle(); Thread.sleep(20); v = read() }
        return v
    }

    @Test fun solvingRecordsAnAttempt() {
        val vm = GameViewModel(app)
        waitFor(read = { vm.loaded.value }) { it }
        runBlocking { vm.start("ifthen", com.therefore.logic.data.Mode.PRACTICE) }
        idle()
        var s: GameState = vm.state.value!!
        // Solve by entering each model step exactly as a hint would.
        var guard = 0
        while (!s.done && guard++ < 20) {
            val h = Proofs.hint(s.puzzle, s.lines)!!

            h.step.formula.show().filter { !it.isWhitespace() }.forEach(vm::key)
            vm.selectRule(h.step.rule)
            h.cites.forEach(vm::toggleCite)
            vm.addLine()
            idle()
            s = vm.state.value!!
        }
        assertTrue("puzzle finished", s.done)
        assertEquals("A", s.result!!.grade)
        val attempts = waitFor(read = { runBlocking { app.container.db.attempts().all().first() } }) { it.isNotEmpty() }
        assertEquals(1, attempts.size)
        val progress = waitFor(read = { runBlocking { app.container.progress.current() } }) { it.course("ifthen").solved == 1 }
        assertEquals(1, progress.course("ifthen").solved)
        assertEquals("A", progress.course("ifthen").best)
    }
}
