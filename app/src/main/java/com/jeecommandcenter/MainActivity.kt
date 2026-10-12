package com.jeecommandcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.jeecommandcenter.data.AppDatabase
import com.jeecommandcenter.data.StudyRepository
import com.jeecommandcenter.ui.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: JeeViewModel by viewModels {
        JeeViewModelFactory(StudyRepository(AppDatabase.get(applicationContext)))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JeeTheme(dark = viewModel.settings.collectAsState().value?.darkTheme ?: true) {
            JeeApp(viewModel)
        } }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(
    Tab("home", "Today", Icons.Default.Home),
    Tab("timeline", "Timeline", Icons.Default.CalendarMonth),
    Tab("backlogs", "Backlogs", Icons.Default.Checklist),
    Tab("syllabus", "Syllabus", Icons.Default.MenuBook),
    Tab("tests", "Tests", Icons.Default.Timer)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JeeApp(vm: JeeViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val timer by vm.timer.collectAsState()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (timer != null) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val title = when {
        route.startsWith("attempt/") -> "Test in progress"
        route.startsWith("result/") -> "Test result"
        route == "analytics" -> "Study analytics"
        route == "settings" -> "Settings & data"
        else -> tabs.firstOrNull { it.route == route }?.label ?: "JEE Command Center"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (!route.startsWith("attempt/")) {
                        IconButton(onClick = { nav.navigate("analytics") }) {
                            Icon(Icons.Default.Analytics, "Analytics")
                        }
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (timer != null && !route.startsWith("attempt/")) {
                val active = timer!!
                val elapsed = com.jeecommandcenter.domain.StudyMath.timerElapsed(
                    tick, active.segmentStartAt, active.accumulatedMillis, active.paused
                )
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${active.title} · ${formatDuration(elapsed)}${if (active.paused) " · paused" else ""}")
                        TextButton(onClick = { nav.navigate("timeline") }) { Text("Open") }
                    }
                }
            }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") { DashboardScreen(vm, onAddActivity = { nav.navigate("timeline") },
                    onAddBacklog = { nav.navigate("backlogs") }, onTests = { nav.navigate("tests") },
                    onAnalytics = { nav.navigate("analytics") }) }
                composable("timeline") { TimelineScreen(vm) }
                composable("backlogs") { BacklogScreen(vm) }
                composable("syllabus") { SyllabusScreen(vm) }
                composable("tests") { TestsScreen(vm, onAttempt = { nav.navigate("attempt/$it") },
                    onResult = { nav.navigate("result/$it") }) }
                composable("analytics") { AnalyticsScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
                composable("attempt/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    AttemptScreen(vm, it.arguments?.getString("id").orEmpty(),
                        onFinished = { nav.navigate("result/$it") })
                }
                composable("result/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    ResultScreen(vm, it.arguments?.getString("id").orEmpty())
                }
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
}

fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
