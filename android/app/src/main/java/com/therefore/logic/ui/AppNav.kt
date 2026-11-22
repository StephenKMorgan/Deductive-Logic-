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
import com.therefore.logic.ui.screens.PredicateScreen
import com.therefore.logic.ui.screens.StatsScreen
import com.therefore.logic.ui.screens.GamesScreen
import com.therefore.logic.ui.screens.GameId
import com.therefore.logic.ui.screens.IslandScreen
import com.therefore.logic.ui.screens.QuizScreen
import com.therefore.logic.ui.screens.BoardScreen
import com.therefore.logic.ui.screens.CourseAction
import com.therefore.logic.ui.screens.CourseScreen
import com.therefore.logic.ui.screens.LessonPlayerScreen
import com.therefore.logic.data.Mode
import androidx.lifecycle.viewmodel.compose.viewModel
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
    const val PREDICATE = "predicate"
    const val GAMES = "games"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val COURSE = "course/{id}"
    const val LESSON = "lesson/{id}"
    const val PLAY = "play"
    const val RESULT = "result"
    fun course(id: String) = "course/$id"
    fun lesson(id: String) = "lesson/$id"
}

private val TABS = listOf(
    Triple(Routes.HOME, "Learn", "∴"), Triple(Routes.PREDICATE, "Predicate", "∀"), Triple(Routes.GAMES, "Games", "◆"),
    Triple(Routes.DAILY, "Daily", "◷"), Triple(Routes.RULES, "Rules", "§"),
)

@Composable
fun ThereforeApp(vm: GameViewModel, settings: Settings, openDaily: Boolean) {
    val context = LocalContext.current
    val container = (context.applicationContext as TheApp).container
    val nav = rememberNavController()
    val gamesVm: GamesViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val progress by container.progress.progress.collectAsStateWithLifecycle(Progress())
    val game by vm.state.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val p = LocalPalette.current

    LaunchedEffect(openDaily) { if (openDaily) nav.navigate(Routes.DAILY) { launchSingleTop = true } }

    /** Resumes a matching puzzle in progress, or generates a new one, then opens the board. */
    fun play(courseId: String, mode: Mode, level: Int? = null, lessonId: String? = null, fresh: Boolean = false, date: java.time.LocalDate = java.time.LocalDate.now()) = scope.launch {
        if (fresh || !vm.hasGame(courseId, mode, lessonId, level, date)) vm.start(courseId, mode, level, lessonId, date)
        withContext(Dispatchers.Main.immediate) {
            val back = if (Curriculum.isDaily(courseId)) Routes.DAILY else Routes.course(courseId)
            nav.navigate(Routes.PLAY) { popUpTo(back); launchSingleTop = true }
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
                    onCourse = { nav.navigate(Routes.course(it)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onStats = { nav.navigate(Routes.STATS) },
                )
            }
            composable(Routes.DAILY) {
                DailyScreen(progress, inProgress = { id, date -> vm.hasGame(id, Mode.DAILY, date = date) }) { id, date -> play(id, Mode.DAILY, date = date) }
            }
            composable(Routes.RULES) { RulesScreen() }
            composable(Routes.PREDICATE) { PredicateScreen(progress) { nav.navigate(Routes.course(it)) } }
            composable(Routes.STATS) {
                val attempts by container.progress.attempts.collectAsStateWithLifecycle(emptyList())
                StatsScreen(com.therefore.logic.data.Stats.compute(attempts, progress, java.time.LocalDate.now()), progress) { nav.popBackStack() }
            }
            composable(Routes.GAMES) { GamesScreen(gamesVm) { nav.navigate(it.route) } }
            composable(GameId.ISLAND.route) { IslandScreen(gamesVm) { nav.popBackStack() } }
            composable(GameId.QUIZ.route) { QuizScreen(gamesVm) { nav.popBackStack() } }
            composable(GameId.BOARD.route) { BoardScreen(gamesVm) { nav.popBackStack() } }
            composable(Routes.COURSE) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                CourseScreen(
                    id, progress,
                    resumeLabel = if (vm.hasGame(id, Mode.PRACTICE)) "Resume practice puzzle" else null,
                    onBack = { nav.popBackStack() },
                ) { action ->
                    when (action) {
                        CourseAction.Practice -> play(id, Mode.PRACTICE)
                        is CourseAction.Lesson -> nav.navigate(Routes.lesson(action.lessonId))
                        is CourseAction.FreePlay -> play(id, Mode.FREE, action.level, action.lessonId, fresh = true)
                    }
                }
            }
            composable(Routes.LESSON) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val lessonVm: LessonViewModel = viewModel()
                LaunchedEffect(id) { lessonVm.open(id) }
                if (lessonVm.state.collectAsStateWithLifecycle().value?.lessonId != id) return@composable
                val courseId = Curriculum.lesson(id).first.id
                LessonPlayerScreen(
                    lessonVm, progress,
                    onClose = { nav.popBackStack() },
                    onNextLesson = { next -> nav.navigate(Routes.lesson(next)) { popUpTo(Routes.course(courseId)) } },
                    onFreePlay = { level -> play(courseId, Mode.FREE, level, id, fresh = true) },
                )
            }
            // These screens redirect when their game is missing or finished, but only while they are the
            // screen on top. A screen that is animating away (e.g. results, after "Next puzzle" has
            // started a new game) must not act, or it would pop the new screen off the stack.
            composable(Routes.PLAY) { entry ->
                val s = game
                val onTop = { nav.currentBackStackEntry?.id == entry.id }
                if (s == null) { LaunchedEffect(Unit) { if (onTop()) nav.popBackStack() }; return@composable }
                LaunchedEffect(s.done) { if (s.done && onTop()) nav.navigate(Routes.RESULT) { popUpTo(Routes.PLAY) { inclusive = true } } }
                if (!s.done) PlayScreen(vm, s, onBack = { nav.popBackStack() })
            }
            composable(Routes.RESULT) { entry ->
                val s = game
                if (s?.result == null) { LaunchedEffect(Unit) { if (nav.currentBackStackEntry?.id == entry.id) nav.popBackStack() }; return@composable }
                val back = {
                    if (s.isDaily) nav.navigate(Routes.DAILY) { popUpTo(Routes.HOME); launchSingleTop = true }
                    else nav.navigate(Routes.course(s.courseId)) { popUpTo(Routes.HOME); launchSingleTop = true }
                }
                ResultScreen(
                    s,
                    onBack = { back() },
                    onNext = {
                        play(s.courseId, s.mode, if (s.mode == Mode.FREE) s.level else null, s.lessonId, fresh = true,
                            date = s.date?.let(java.time.LocalDate::parse) ?: java.time.LocalDate.now())
                    },
                    onLesson = { back() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings, progress,
                    onBack = { nav.popBackStack() },
                    onPlainText = { v -> container.scope.launch { container.settings.setPlainText(v) } },
                    onTheme = { v: ThemeMode -> container.scope.launch { container.settings.setTheme(v) } },
                    onAccent = { v -> container.scope.launch { container.settings.setAccent(v) } },
                    onTextSize = { v -> container.scope.launch { container.settings.setTextSize(v) } },
                    onStats = { nav.navigate(Routes.STATS) },
                    onHaptics = { v -> container.scope.launch { container.settings.setHaptics(v) } },
                    onReminder = { on, at ->
                        container.scope.launch { container.settings.setReminder(on, at) }
                        if (on) Reminders.schedule(context, at) else Reminders.cancel(context)
                    },
                    onReset = { vm.resetAll(); container.scope.launch { container.games.reset() } },
                )
            }
        }
    }
}
