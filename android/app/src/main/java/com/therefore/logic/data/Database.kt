package com.therefore.logic.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One finished puzzle. Course progress, grades and streaks are all worked out from these rows. */
@Entity(tableName = "attempts")
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: String,
    val level: Int,
    /** Local date the puzzle was finished, ISO format (yyyy-mm-dd). For the daily proof, the puzzle's date. */
    val date: String,
    /** A–D, or "–" when the solution was revealed. */
    val grade: String,
    val stars: Int,
    val score: Int,
    val lines: Int,
    val modelLines: Int,
    val mistakes: Int,
    val hints: Int,
    val seconds: Long,
    val revealed: Boolean,
    val finishedAt: Long,
    /** PRACTICE, FREE or DAILY (see [Mode]). Only practice raises a course's level. */
    @ColumnInfo(defaultValue = "PRACTICE") val mode: String = Mode.PRACTICE.name,
)

/** A lesson the learner has finished at least once. */
@Entity(tableName = "lessons_done")
data class LessonDone(@PrimaryKey val lessonId: String, val finishedAt: Long)

/** The puzzle in progress (a single row), stored as JSON so it survives the app being closed. */
@Entity(tableName = "saved_game")
data class SavedGame(@PrimaryKey val id: Int = 0, val json: String)

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: Attempt)

    @Query("SELECT * FROM attempts ORDER BY finishedAt")
    fun all(): Flow<List<Attempt>>

    @Query("SELECT COUNT(*) FROM attempts WHERE courseId IN ('daily', 'daily-pred') AND date = :date AND revealed = 0")
    suspend fun dailyCount(date: String): Int

    @Query("DELETE FROM attempts")
    suspend fun clear()
}

@Dao
interface LessonDao {
    @Upsert
    suspend fun markDone(done: LessonDone)

    @Query("SELECT lessonId FROM lessons_done")
    fun done(): Flow<List<String>>

    @Query("DELETE FROM lessons_done")
    suspend fun clear()
}

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_game WHERE id = 0")
    suspend fun get(): SavedGame?

    @Upsert
    suspend fun put(game: SavedGame)

    @Query("DELETE FROM saved_game")
    suspend fun clear()
}

@Database(entities = [Attempt::class, SavedGame::class, LessonDone::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attempts(): AttemptDao
    abstract fun savedGame(): SavedGameDao
    abstract fun lessons(): LessonDao

    companion object {
        /** Version 2: attempts gain a mode (practice / free play / daily); finished lessons are recorded. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN mode TEXT NOT NULL DEFAULT 'PRACTICE'")
                db.execSQL("UPDATE attempts SET mode = 'DAILY' WHERE courseId = 'daily'")
                db.execSQL("CREATE TABLE IF NOT EXISTS lessons_done (lessonId TEXT NOT NULL, finishedAt INTEGER NOT NULL, PRIMARY KEY(lessonId))")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "therefore.db").addMigrations(MIGRATION_1_2).build()
    }
}
