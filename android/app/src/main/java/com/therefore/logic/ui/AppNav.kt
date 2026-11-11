package com.therefore.logic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.therefore.logic.TheApp
import com.therefore.logic.content.Curriculum
import com.therefore.logic.data.Progress
import com.therefore.logic.data.Settings
import com.therefore.logic.data.ThemeMode
import com.therefore.logic.reminder.Reminders
import com.therefore.logic.ui.screens.DailyScreen
import com.therefore.logic.ui.screens.HomeScreen
import com.therefore.logic.ui.screens.LessonScreen
import com.therefore.logic.ui.screens.PlayScreen
import com.therefore.logic.ui.screens.ResultScreen
import com.therefore.logic.ui.screens.RulesScreen
import com.therefore.logic.ui.screens.SettingsScreen
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val HOME = "home"
    const val DAILY = "daily"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val LESSON = "lesson/{id}"
    const val PLAY = "play"
    const val RESULT = "result"
    fun lesson(id: String) = "lesson/$id"
}

private val TABS = listOf(Triple(Routes.HOME, "Learn", "∴"), Triple(Routes.DAILY, "Daily", "◷"), Triple(Routes.RULES, "Rules", "§"))

@Composable
fun ThereforeApp(vm: GameViewModel, settings: Settings, openDaily: Boolean) {
    val context = LocalContext.current
    val container = (context.applicationContext as TheApp).container
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val progress by container.progress.progress.collectAsStateWithLifecycle(Progress())
    val game by vm.state.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val p = LocalPalette.current

    LaunchedEffect(openDaily) { if (openDaily) nav.navigate(Routes.DAILY) { launchSingleTop = true } }

    /** Resumes the course's puzzle in progress, or generates a new one. */
    fun play(courseId: String, fresh: Boolean = false) = scope.launch {
        if (fresh || !vm.hasGameFor(courseId)) vm.start(courseId)
        withContext(Dispatchers.Main.immediate) {
            nav.navigate(Routes.PLAY) { popUpTo(Routes.HOME); launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = p.bg,
        bottomBar = {
            if (route in TABS.map { it.first }) NavigationBar(containerColor = p.surface) {
                for ((r, label, icon) in TABS) NavigationBarItem(
                    selected = route == r,
                    onClick = { nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Text(icon, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = p.accentSoft, selectedTextColor = p.accent, selectedIconColor = p.accent),
                )
            }
        },
    ) { pad ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        NavHost(nav, startDestination = Routes.HOME, Modifier.padding(pad)) {
            composable(Routes.HOME) {
                HomeScreen(
                    progress, game,
                    onResume = { nav.navigate(if (game?.done == true) Routes.RESULT else Routes.PLAY) },
                    onDaily = { nav.navigate(Routes.DAILY) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true } },
                    onCourse = { nav.navigate(Routes.lesson(it)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.DAILY) {
                DailyScreen(progress, vm.hasGameFor(Curriculum.DAILY_ID)) { play(Curriculum.DAILY_ID) }
            }
            composable(Routes.RULES) { RulesScreen() }
            composable(Routes.LESSON) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                LessonScreen(id, progress, onBack = { nav.popBackStack() }, onStart = { play(id) })
            }
            composable(Routes.PLAY) {
                val s = game
                if (s == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                LaunchedEffect(s.done) { if (s.done) nav.navigate(Routes.RESULT) { popUpTo(Routes.PLAY) { inclusive = true } } }
                if (!s.done) PlayScreen(vm, s, onBack = { nav.popBackStack() })
            }
            composable(Routes.RESULT) {
                val s = game
                if (s?.result == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                val back = { nav.navigate(if (s.isDaily) Routes.DAILY else Routes.HOME) { popUpTo(Routes.HOME); launchSingleTop = true } }
                ResultScreen(
                    s,
                    onBack = { back() },
                    onNext = { play(s.courseId, fresh = true) },
                    onLesson = { nav.navigate(Routes.lesson(s.courseId)) { popUpTo(Routes.HOME) } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings, progress,
                    onBack = { nav.popBackStack() },
                    onPlainText = { v -> container.scope.launch { container.settings.setPlainText(v) } },
                    onTheme = { v: ThemeMode -> container.scope.launch { container.settings.setTheme(v) } },
                    onHaptics = { v -> container.scope.launch { container.settings.setHaptics(v) } },
                    onReminder = { on, at ->
                        container.scope.launch { container.settings.setReminder(on, at) }
                        if (on) Reminders.schedule(context, at) else Reminders.cancel(context)
                    },
                    onReset = { vm.resetAll() },
                )
            }
        }
    }
}
