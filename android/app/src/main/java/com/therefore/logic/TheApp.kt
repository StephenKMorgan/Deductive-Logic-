package com.therefore.logic

import android.app.Application
import com.therefore.logic.data.AppDatabase
import com.therefore.logic.data.ProgressRepository
import com.therefore.logic.data.SettingsRepository
import com.therefore.logic.haptics.Haptics
import com.therefore.logic.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared app objects, created once. */
class AppContainer(app: Application) {
    val db = AppDatabase.create(app)
    val settings = SettingsRepository(app)
    val progress = ProgressRepository(db.attempts(), db.lessons())
    val haptics = Haptics(app)
    val scope = CoroutineScope(SupervisorJob())
}

class TheApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        container.scope.launch {
            container.settings.settings.collect { container.haptics.enabled = it.haptics }
        }
    }
}
