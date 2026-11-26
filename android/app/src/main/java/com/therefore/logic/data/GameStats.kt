package com.therefore.logic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GameStats(
    val islandSolved: Int = 0,
    val islandStreak: Int = 0,
    val islandBestStreak: Int = 0,
    val quizBest: Int = 0,
    val quizRounds: Int = 0,
    val boardSolved: Int = 0,
    val boardPerfect: Int = 0,
    val solverSolved: Int = 0,
    val solverPar: Int = 0,
)

private val Context.gameStore: DataStore<Preferences> by preferencesDataStore("games")

/** Scores for the Games tab. */
class GameStatsRepository(private val context: Context) {
    private object K {
        val islandSolved = intPreferencesKey("island_solved")
        val islandStreak = intPreferencesKey("island_streak")
        val islandBest = intPreferencesKey("island_best_streak")
        val quizBest = intPreferencesKey("quiz_best")
        val quizRounds = intPreferencesKey("quiz_rounds")
        val boardSolved = intPreferencesKey("board_solved")
        val boardPerfect = intPreferencesKey("board_perfect")
        val solverSolved = intPreferencesKey("solver_solved")
        val solverPar = intPreferencesKey("solver_par")
    }

    val stats: Flow<GameStats> = context.gameStore.data.map { p ->
        GameStats(
            p[K.islandSolved] ?: 0, p[K.islandStreak] ?: 0, p[K.islandBest] ?: 0,
            p[K.quizBest] ?: 0, p[K.quizRounds] ?: 0, p[K.boardSolved] ?: 0, p[K.boardPerfect] ?: 0,
            p[K.solverSolved] ?: 0, p[K.solverPar] ?: 0,
        )
    }

    suspend fun island(correct: Boolean) = context.gameStore.edit {
        if (correct) {
            val streak = (it[K.islandStreak] ?: 0) + 1
            it[K.islandStreak] = streak
            it[K.islandSolved] = (it[K.islandSolved] ?: 0) + 1
            it[K.islandBest] = maxOf(it[K.islandBest] ?: 0, streak)
        } else it[K.islandStreak] = 0
    }

    suspend fun quiz(score: Int) = context.gameStore.edit {
        it[K.quizRounds] = (it[K.quizRounds] ?: 0) + 1
        it[K.quizBest] = maxOf(it[K.quizBest] ?: 0, score)
    }

    suspend fun board(perfect: Boolean) = context.gameStore.edit {
        it[K.boardSolved] = (it[K.boardSolved] ?: 0) + 1
        if (perfect) it[K.boardPerfect] = (it[K.boardPerfect] ?: 0) + 1
    }

    suspend fun solver(withinPar: Boolean) = context.gameStore.edit {
        it[K.solverSolved] = (it[K.solverSolved] ?: 0) + 1
        if (withinPar) it[K.solverPar] = (it[K.solverPar] ?: 0) + 1
    }

    suspend fun reset() = context.gameStore.edit { it.clear() }
}
