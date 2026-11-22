package com.therefore.logic.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Upgrading keeps every finished puzzle: version 1 → 2 → 3. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun upgradesKeepHistory() {
        helper.createDatabase("migrate.db", 1).apply {
            execSQL(
                "INSERT INTO attempts (courseId, level, date, grade, stars, score, lines, modelLines, mistakes, hints, seconds, revealed, finishedAt) " +
                    "VALUES ('ifthen', 3, '2026-11-01', 'A', 3, 95, 4, 4, 0, 0, 60, 0, 1000), ('daily', 3, '2026-11-02', 'B', 2, 85, 5, 4, 1, 0, 90, 0, 2000)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migrate.db", 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        db.query("SELECT courseId, mode, rulesUsed, mistakeRules FROM attempts ORDER BY finishedAt").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst(); assertEquals("ifthen", c.getString(0)); assertEquals("PRACTICE", c.getString(1)); assertEquals("", c.getString(2))
            c.moveToNext(); assertEquals("daily", c.getString(0)); assertEquals("DAILY", c.getString(1))
        }
    }
}
