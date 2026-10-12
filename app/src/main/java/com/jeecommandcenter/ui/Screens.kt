package com.jeecommandcenter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeecommandcenter.JeeViewModel
import com.jeecommandcenter.data.*
import com.jeecommandcenter.domain.StudyMath
import com.jeecommandcenter.domain.TestScoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val subjects = listOf("Physics", "Chemistry", "Mathematics", "Other")
private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private fun localMillis(dateTime: String): Long? = runCatching {
    LocalDateTime.parse(dateTime.trim(), dateTimeFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
private fun dateTimeText(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).toLocalDateTime().format(dateTimeFormat)
private fun dayBounds(day: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
private fun todayActivities(all: List<ActivityRecord>): List<ActivityRecord> {
    val (from, to) = dayBounds(LocalDate.now())
    return all.filter { it.startAt < to && it.endAt > from }
}
private fun isStudy(activity: ActivityRecord) = activity.kind in setOf("Study", "Lecture", "Practice", "Revision", "Test", "Assignment")
private fun durationWithin(activity: ActivityRecord, from: Long, to: Long): Long {
    val interval = (activity.endAt - activity.startAt).coerceAtLeast(0L)
    if (interval == 0L) return 0L
    val overlap = (minOf(activity.endAt, to) - maxOf(activity.startAt, from)).coerceAtLeast(0L)
    return (activity.durationMillis.toDouble() * overlap / interval).toLong()
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextFieldValue(value: String, onChange: (String) -> Unit, label: String,
                           modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(value: String, values: List<String>, label: String, onSelect: (String) -> Unit,
                        modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: JeeViewModel, onAddActivity: () -> Unit, onAddBacklog: () -> Unit,
                    onTests: () -> Unit, onAnalytics: () -> Unit) {
    val all by vm.activities.collectAsStateWithLifecycle()
    val backlogs by vm.backlogs.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = todayActivities(all)
    val (dayStart, dayEnd) = dayBounds(LocalDate.now())
    val study = today.filter(::isStudy).sumOf { durationWithin(it, dayStart, dayEnd) }
    var timerForm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val goal = settings?.dailyGoalMinutes ?: 360
    val weekHours = (0L..6L).sumOf { day ->
        val (from, to) = dayBounds(LocalDate.now().minusDays(6 - day))
        all.filter(::isStudy).sumOf { durationWithin(it, from, to) }
    } / 3_600_000.0
    val streak = remember(all) {
        val days = all.filter(::isStudy).map {
            Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        var cursor = LocalDate.now()
        if (cursor !in days) cursor = cursor.minusDays(1)
        var count = 0
        while (cursor in days) { count++; cursor = cursor.minusDays(1) }
        count
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val journeyDay = (((dayStart - (settings?.journeyStartAt ?: dayStart)) / 86_400_000L) + 1).coerceAtLeast(1)
        Text("${settings?.nickname?.takeIf(String::isNotBlank)?.let { "$it · " } ?: ""}Day $journeyDay of your preparation",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Study today", "${study / 3_600_000}h ${(study / 60_000 % 60)}m", Modifier.weight(1f))
            Metric("Target", "$goal min", Modifier.weight(1f))
        }
        LinearProgressIndicator(progress = { (study / 60_000.0 / goal.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("This week", "%.1f h".format(weekHours), Modifier.weight(1f))
            Metric("Study streak", "$streak days", Modifier.weight(1f))
        }
        Heading("Today by subject")
        subjects.take(3).forEach { subject ->
            val minutes = today.filter { isStudy(it) && it.subject == subject }
                .sumOf { durationWithin(it, dayStart, dayEnd) } / 60_000
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(subject); Text("$minutes min", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Heading("Preparation overview")
        val completed = chapters.count { it.status == "Completed" }
        Text("Chapters: $completed completed · ${chapters.size - completed} remaining")
        val openBacklogs = backlogs.filter { it.status != "Completed" }
        Text("Backlogs: ${openBacklogs.size} open · ${openBacklogs.count { it.deadlineAt != null && it.deadlineAt < System.currentTimeMillis() }} overdue")
        Heading("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { timerForm = true }, modifier = Modifier.weight(1f)) { Text(if (timer == null) "Start study" else "Timer active") }
            OutlinedButton(onClick = onAddActivity, modifier = Modifier.weight(1f)) { Text("Add activity") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddBacklog, modifier = Modifier.weight(1f)) { Text("Add backlog") }
            OutlinedButton(onClick = onTests, modifier = Modifier.weight(1f)) { Text("Create test") }
        }
        OutlinedButton(onClick = onAnalytics, modifier = Modifier.fillMaxWidth()) { Text("View study analytics") }
        Heading("Recent activities")
        if (today.isEmpty()) Text("No activities recorded today. Only saved entries appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        today.sortedByDescending { it.startAt }.take(5).forEach { ActivityRow(it) }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
    if (timerForm) TimerDialog(vm, onError = { message = it }, onClose = { timerForm = false })
}

@Composable
private fun ActivityRow(activity: ActivityRecord, trailing: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(activity.title, fontWeight = FontWeight.Medium)
                Text("${activity.subject} · ${activity.kind} · ${com.jeecommandcenter.formatTime(activity.startAt)}–${com.jeecommandcenter.formatTime(activity.endAt)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Recorded duration ${com.jeecommandcenter.formatDuration(activity.durationMillis)} · ${activity.source}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun TimerDialog(vm: JeeViewModel, onError: (String) -> Unit, onClose: () -> Unit,
                        backlog: BacklogItem? = null) {
    var title by remember { mutableStateOf(backlog?.title ?: "") }
    var subject by remember { mutableStateOf(backlog?.subject ?: "Physics") }
    var chapter by remember { mutableStateOf(backlog?.chapter ?: "") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Start a study session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldValue(title, { title = it }, "Session title")
                SelectField(subject, subjects, "Subject") { subject = it }
                TextFieldValue(chapter, { chapter = it }, "Chapter (optional)")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "Add a session title."; return@TextButton }
                vm.startTimer(title, subject, chapter, backlog?.id, { error = it; onError(it) })
                onClose()
            }) { Text("Start") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun TimelineScreen(vm: JeeViewModel) {
    val all by vm.activities.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val (start, end) = dayBounds(selectedDay)
    val entries = all.filter { it.startAt < end && it.endAt > start }.sortedBy { it.startAt }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedDay = selectedDay.minusDays(1) }) { Text("‹ Previous") }
            Text(selectedDay.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { selectedDay = selectedDay.plusDays(1) }) { Text("Next ›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text("Add activity") }
            OutlinedButton(onClick = { showTimer = true }, enabled = timer == null) { Text("Start timer") }
        }
        if (timer != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (timer!!.paused) "Session paused" else "Timer is running", Modifier.weight(1f))
                TextButton(onClick = { if (timer!!.paused) vm.resumeTimer() else vm.pauseTimer() }) {
                    Text(if (timer!!.paused) "Resume" else "Pause")
                }
                TextButton(onClick = { confirmStop = true }) { Text("Stop & save") }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Heading("Recorded timeline")
        Text("Entries retain their recorded source. Empty time is unrecorded, not estimated.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entries.isEmpty()) Text("Nothing recorded for this date.")
        LazyColumn {
            var cursor = start
            entries.forEach { entry ->
                val clippedStart = maxOf(start, entry.startAt)
                val clippedEnd = minOf(end, entry.endAt)
                if (clippedStart > cursor) item(key = "gap-$cursor") {
                    Text("Unrecorded · ${com.jeecommandcenter.formatTime(cursor)}–${com.jeecommandcenter.formatTime(clippedStart)}",
                        Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item(key = entry.id) {
                    ActivityRow(entry) {
                        TextButton(onClick = { vm.deleteActivity(entry.id) }) { Text("Delete") }
                    }
                }
                cursor = maxOf(cursor, clippedEnd)
            }
            if (cursor < end && entries.isNotEmpty()) item { Text("Unrecorded · ${com.jeecommandcenter.formatTime(cursor)}–23:59:59",
                Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (showAdd) ActivityDialog(vm, selectedDay, { showAdd = false }, { error = it })
    if (showTimer) TimerDialog(vm, { error = it }, { showTimer = false })
    if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false },
        title = { Text("Save this study session?") },
        text = { Text("Only active timer time is recorded. Stopping does not mark a linked backlog complete.") },
        confirmButton = { TextButton(onClick = { vm.stopTimer(); confirmStop = false }) { Text("Stop and save") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep running") } })
}

@Composable
private fun ActivityDialog(vm: JeeViewModel, day: LocalDate, onClose: () -> Unit, onError: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val initialStart = if (day == LocalDate.now()) maxOf(now - 3_600_000, dayStart) else dayStart + 9 * 3_600_000
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(dateTimeText(initialStart)) }
    var end by remember { mutableStateOf(dateTimeText(initialStart + 3_600_000)) }
    var subject by remember { mutableStateOf("Physics") }
    var chapter by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Study") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Add an activity") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextFieldValue(title, { title = it }, "What did you do?")
                Text("Use local time in YYYY-MM-DD HH:mm:ss format.", style = MaterialTheme.typography.labelSmall)
                TextFieldValue(start, { start = it }, "Start date and time")
                TextFieldValue(end, { end = it }, "End date and time")
                SelectField(subject, subjects, "Subject") { subject = it }
                TextFieldValue(chapter, { chapter = it }, "Chapter or topic")
                SelectField(kind, listOf("Study", "Lecture", "Practice", "Revision", "Test", "Assignment", "Break", "Personal", "Other"), "Activity type") { kind = it }
                TextFieldValue(notes, { notes = it }, "Notes", singleLine = false)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(onClick = {
                val s = localMillis(start); val e = localMillis(end)
                if (s == null || e == null) { error = "Enter valid date and time values."; return@TextButton }
                vm.addActivity(title, s, e, subject, chapter, kind, notes) { error = it; onError(it) }
                onClose()
            }) { Text("Save activity") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun BacklogScreen(vm: JeeViewModel) {
    val items by vm.backlogs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Open") }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BacklogItem?>(null) }
    var timerItem by remember { mutableStateOf<BacklogItem?>(null) }
    var showTimer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val visible = items.filter { it.title.contains(query, true) || it.chapter.contains(query, true) }
        .filter { status == "All" || (if (status == "Open") it.status != "Completed" else it.status == "Completed") }
    val remaining = visible.filter { it.status != "Completed" }.sumOf(StudyMath::remainingMinutes)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectField(status, listOf("Open", "Completed", "All"), "Show",
                onSelect = { status = it }, modifier = Modifier.weight(1f))
            Button(onClick = { editing = null; add = true }) { Text("Add") }
        }
        TextFieldValue(query, { query = it }, "Search backlogs")
        Text("${visible.count { it.status != "Completed" }} items · ${remaining / 60}h ${remaining % 60}m estimated remaining",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        if (visible.isEmpty()) Text("No matching backlog items. Add a task to start tracking work.")
        LazyColumn {
            items(visible, key = { it.id }) { item ->
                val overdue = item.deadlineAt?.let { it < System.currentTimeMillis() && item.status != "Completed" } == true
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold)
                                Text("${item.subject} · ${item.chapter.ifBlank { "No chapter" }} · ${item.type}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                val next = when (item.status) {
                                    "Not started" -> "In progress"
                                    "In progress" -> "Paused"
                                    "Paused" -> "Completed"
                                    "Completed" -> "Not started"
                                    else -> "Not started"
                                }
                                vm.updateBacklog(item.copy(status = next))
                            }) { Text(item.status) }
                        }
                        LinearProgressIndicator(progress = { StudyMath.backlogCompletion(item) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Text("${StudyMath.backlogCompletion(item)}% · ${item.actualMinutes}/${item.estimatedMinutes} min · ${item.priority} priority" +
                            if (overdue) " · OVERDUE" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { timerItem = item; showTimer = true }) { Text("Study") }
                            TextButton(onClick = { editing = item; add = true }) { Text("Edit") }
                            TextButton(onClick = {
                                val now = System.currentTimeMillis()
                                vm.updateBacklog(item.copy(id = UUID.randomUUID().toString(), title = "${item.title} (copy)",
                                    status = "Not started", actualMinutes = 0, parentId = item.id, createdAt = now, updatedAt = now))
                            }) { Text("Duplicate") }
                            TextButton(onClick = { vm.deleteBacklog(item.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
    if (add) BacklogDialog(vm, editing, { add = false }, { message = it })
    if (showTimer && timerItem != null) TimerDialog(vm, { message = it }, { showTimer = false }, timerItem)
}

@Composable
private fun BacklogDialog(vm: JeeViewModel, initial: BacklogItem?, onClose: () -> Unit,
                          onError: (String) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var subject by remember(initial) { mutableStateOf(initial?.subject ?: "Physics") }
    var chapter by remember(initial) { mutableStateOf(initial?.chapter ?: "") }
    var type by remember(initial) { mutableStateOf(initial?.type ?: "Practice") }
    var priority by remember(initial) { mutableStateOf(initial?.priority ?: "Medium") }
    var estimate by remember(initial) { mutableStateOf((initial?.estimatedMinutes ?: 60).toString()) }
    var deadline by remember(initial) { mutableStateOf(initial?.deadlineAt?.let(::dateTimeText) ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (initial == null) "Add backlog" else "Edit backlog") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextFieldValue(title, { title = it }, "Task")
                SelectField(subject, subjects, "Subject") { subject = it }
                TextFieldValue(chapter, { chapter = it }, "Chapter or topic")
                SelectField(type, listOf("Lecture", "Notes", "DPP", "Module", "PYQ", "Revision", "Assignment", "Test", "Custom"), "Type") { type = it }
                SelectField(priority, listOf("Low", "Medium", "High", "Critical"), "Priority") { priority = it }
                TextFieldValue(estimate, { estimate = it.filter(Char::isDigit) }, "Estimated minutes")
                TextFieldValue(deadline, { deadline = it }, "Deadline (YYYY-MM-DD HH:mm:ss; optional)")
                TextFieldValue(notes, { notes = it }, "Notes", singleLine = false)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(onClick = {
                val minutes = estimate.toIntOrNull()
                if (title.isBlank() || minutes == null || minutes < 0) { error = "Add a title and valid estimated minutes."; return@TextButton }
                val due = if (deadline.isBlank()) null else localMillis(deadline)
                if (deadline.isNotBlank() && due == null) { error = "Enter a valid deadline."; return@TextButton }
                if (initial == null) vm.addBacklog(title, subject, chapter, type, priority, minutes, due, notes) {
                    error = it; onError(it)
                } else vm.updateBacklog(initial.copy(title = title.trim(), subject = subject, chapter = chapter,
                    type = type, priority = priority, estimatedMinutes = minutes, deadlineAt = due, notes = notes))
                onClose()
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun SyllabusScreen(vm: JeeViewModel) {
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    var subject by remember { mutableStateOf("Physics") }
    var level by remember { mutableIntStateOf(11) }
    var add by remember { mutableStateOf(false) }
    val filtered = chapters.filter { it.subject == subject && it.schoolClass == level }
    val completed = filtered.count { it.status == "Completed" }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(subject, subjects.take(3), "Subject", { subject = it }, Modifier.weight(1f))
            SelectField("Class $level", listOf("Class 11", "Class 12"), "Class",
                { level = if (it.endsWith("11")) 11 else 12 }, Modifier.weight(1f))
        }
        LinearProgressIndicator(progress = { if (filtered.isEmpty()) 0f else completed.toFloat() / filtered.size },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Text("$completed of ${filtered.size} chapters complete · ${filtered.size - completed} remaining",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = { add = true }) { Text("Add custom chapter") }
        Text("Starter chapter list is editable; verify it against your current exam syllabus.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(filtered, key = { it.id }) { chapter ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, fontWeight = FontWeight.Medium)
                            Text("Confidence ${chapter.confidence}/5 · difficulty ${chapter.difficulty}/5",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            val next = when (chapter.status) {
                                "Not started" -> "Studying"
                                "Studying" -> "Revision needed"
                                "Revision needed" -> "Completed"
                                else -> "Not started"
                            }
                            vm.updateChapter(chapter.copy(status = next,
                                lastStudiedAt = System.currentTimeMillis()))
                        }) { Text(chapter.status) }
                        TextButton(onClick = { vm.deleteChapter(chapter.id) }) { Text("Remove") }
                    }
                }
            }
        }
    }
    if (add) AddChapterDialog(vm, { add = false })
}

@Composable
private fun AddChapterDialog(vm: JeeViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Physics") }
    var level by remember { mutableIntStateOf(11) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Add chapter") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFieldValue(title, { title = it }, "Chapter name")
            SelectField(subject, subjects.take(3), "Subject") { subject = it }
            SelectField("Class $level", listOf("Class 11", "Class 12"), "Class") { level = if (it.endsWith("11")) 11 else 12 }
        }
    }, confirmButton = { TextButton(onClick = {
        if (title.isNotBlank()) vm.updateChapter(Chapter(UUID.randomUUID().toString(), subject, level,
            title.trim(), "Not started", false, false, false, false, 0, 3, null, null, ""))
        onClose()
    }) { Text("Add") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun TestsScreen(vm: JeeViewModel, onAttempt: (String) -> Unit, onResult: (String) -> Unit) {
    val tests by vm.tests.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    var questionFor by remember { mutableStateOf<PracticeTest?>(null) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create a test, add your own questions and marking rules, then take it offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { create = true }, modifier = Modifier.padding(vertical = 10.dp)) { Text("Create test") }
        if (tests.isEmpty()) Text("No tests yet. Create a test to add questions and start an attempt.")
        LazyColumn {
            items(tests, key = { it.id }) { test ->
                val questions by vm.questions(test.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val attempts by vm.attempts(test.id).collectAsStateWithLifecycle(initialValue = emptyList())
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(test.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${test.durationMinutes} min · ${questions.size} questions · ${attempts.size} saved attempts")
                        Row {
                            TextButton(onClick = { questionFor = test }) { Text("Add question") }
                            TextButton(onClick = {
                                if (questions.isEmpty()) error = "Add at least one question before starting."
                                else vm.startAttempt(test.id, onAttempt)
                            }) { Text("Start / resume") }
                        }
                        attempts.firstOrNull()?.let { attempt ->
                            TextButton(onClick = {
                                if (attempt.status == "in_progress") onAttempt(attempt.id) else onResult(attempt.id)
                            }) {
                                Text(if (attempt.status == "in_progress") "Open saved attempt" else "View latest result")
                            }
                        }
                        if (questions.isNotEmpty()) Text(
                            "Question types: ${questions.map { it.type.replace('_', ' ') }.distinct().joinToString()}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
    if (create) CreateTestDialog(vm, { create = false })
    if (questionFor != null) AddQuestionDialog(vm, questionFor!!, { questionFor = null }, { error = it })
}

@Composable
private fun CreateTestDialog(vm: JeeViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var instructions by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Create a test") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFieldValue(title, { title = it }, "Test name")
            TextFieldValue(duration, { duration = it.filter(Char::isDigit) }, "Duration in minutes")
            TextFieldValue(instructions, { instructions = it }, "Instructions", singleLine = false)
        }
    }, confirmButton = { TextButton(onClick = {
        val minutes = duration.toIntOrNull()
        if (title.isNotBlank() && minutes != null && minutes > 0) {
            vm.createTest(title, minutes, instructions) { }
            onClose()
        }
    }) { Text("Create") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun AddQuestionDialog(vm: JeeViewModel, test: PracticeTest, onClose: () -> Unit,
                             onError: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("single_correct") }
    var options by remember { mutableStateOf("Option 1\nOption 2\nOption 3\nOption 4") }
    var correct by remember { mutableStateOf("1") }
    var subject by remember { mutableStateOf("Physics") }
    var chapter by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("4") }
    var negative by remember { mutableStateOf("1") }
    var partial by remember { mutableStateOf("0") }
    var tolerance by remember { mutableStateOf("0") }
    var explanation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Add question") }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TextFieldValue(text, { text = it }, "Question text", singleLine = false)
            SelectField(type, listOf("single_correct", "multiple_correct", "numerical", "true_false"), "Question type") { type = it }
            if (type != "numerical") TextFieldValue(options, { options = it }, "Options (one per line)", singleLine = false)
            TextFieldValue(correct, { correct = it }, if (type == "numerical") "Expected number" else "Correct option number(s), e.g. 1 or 1,3")
            Text("For multiple correct, option numbers are separated by commas. For true/false, enter 1 for True or 2 for False.",
                style = MaterialTheme.typography.labelSmall)
            SelectField(subject, subjects, "Subject") { subject = it }
            TextFieldValue(chapter, { chapter = it }, "Chapter or topic")
            TextFieldValue(marks, { marks = it }, "Marks if correct")
            TextFieldValue(negative, { negative = it }, "Negative marks")
            TextFieldValue(partial, { partial = it }, "Partial marks for a partial multi-select")
            if (type == "numerical") TextFieldValue(tolerance, { tolerance = it }, "Numerical tolerance")
            TextFieldValue(explanation, { explanation = it }, "Solution / explanation", singleLine = false)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val answerTokens = if (type == "numerical") listOf(correct.trim()) else
            correct.split(",").map(String::trim).filter(String::isNotBlank).distinct()
        val choiceList = if (type == "numerical") emptyList() else options.lines().map(String::trim).filter(String::isNotBlank)
        val score = marks.toDoubleOrNull(); val penalty = negative.toDoubleOrNull(); val part = partial.toDoubleOrNull()
        if (text.isBlank() || answerTokens.isEmpty() || score == null || penalty == null || part == null ||
            (type != "numerical" && choiceList.size < 2) ||
            (type != "numerical" && answerTokens.any {
                val optionNumber = it.toIntOrNull()
                optionNumber == null || optionNumber !in 1..choiceList.size
            })) {
            error = "Check question text, answer, options, and non-negative marking values."
            return@TextButton
        }
        if (score < 0 || penalty < 0 || part < 0) { error = "Marks cannot be negative."; return@TextButton }
        vm.addQuestion(TestQuestion(UUID.randomUUID().toString(), test.id, 0,
            type, text.trim(), choiceList.joinToString("\n"),
            answerTokens.joinToString("|"), tolerance.toDoubleOrNull() ?: 0.0,
            explanation.trim(), subject, chapter, score, penalty, part))
        onClose()
    }) { Text("Save question") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun AttemptScreen(vm: JeeViewModel, attemptId: String, onFinished: (String) -> Unit) {
    val attempt by vm.attempt(attemptId).collectAsStateWithLifecycle(initialValue = null)
    val tests by vm.tests.collectAsStateWithLifecycle()
    val current = attempt
    val test = tests.firstOrNull { it.id == current?.testId }
    val questions by vm.questions(current?.testId.orEmpty()).collectAsStateWithLifecycle(initialValue = emptyList())
    val responses by vm.responses(attemptId).collectAsStateWithLifecycle(initialValue = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var autoSubmitted by remember(attemptId) { mutableStateOf(false) }
    LaunchedEffect(attemptId) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val complete = current?.status == "completed"
    val remaining = if (test != null && current != null) {
        (test.durationMinutes * 60_000L - (now - current.startedAt)).coerceAtLeast(0)
    } else 0L
    LaunchedEffect(remaining, complete) {
        if (test != null && current != null && remaining == 0L && !complete && !autoSubmitted && questions.isNotEmpty()) {
            autoSubmitted = true
            vm.saveAttempt(current.copy(status = "completed", finishedAt = now))
            onFinished(attemptId)
        }
    }
    if (current == null || test == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val index = current.currentIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    val question = questions.getOrNull(index)
    val response = responses.firstOrNull { it.questionId == question?.id }
    val chosen = response?.answerJson?.split("|")?.filter(String::isNotBlank).orEmpty().toSet()
    var numerical by remember(question?.id) { mutableStateOf(chosen.firstOrNull().orEmpty()) }
    val openedAt = remember(question?.id) { now }
    fun storeAnswer(answers: Set<String>) {
        question?.let {
            vm.saveResponse(TestResponse("${attemptId}:${it.id}", attemptId, it.id,
                answers.sorted().joinToString("|"), (System.currentTimeMillis() - openedAt).coerceAtLeast(0),
                response?.markedForReview ?: false))
        }
    }
    fun finish() {
        vm.saveAttempt(current.copy(status = "completed", finishedAt = System.currentTimeMillis()))
        onFinished(attemptId)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(test.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(com.jeecommandcenter.formatDuration(remaining), fontWeight = FontWeight.Bold,
                color = if (remaining < 60_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Text("Answered ${responses.count { it.answerJson.isNotBlank() }} · Marked ${responses.count { it.markedForReview }} · ${questions.size} total",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            questions.forEachIndexed { i, q ->
                val saved = responses.firstOrNull { it.questionId == q.id }
                val label = "${i + 1}${if (saved?.markedForReview == true) "★" else ""}${if (!saved?.answerJson.isNullOrBlank()) "•" else ""}"
                OutlinedButton(onClick = { vm.saveAttempt(current.copy(currentIndex = i)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(label) }
            }
        }
        if (question == null) {
            Text("No questions found in this test.")
        } else {
            Text("Question ${index + 1} of ${questions.size} · ${question.subject} · ${question.chapter}",
                style = MaterialTheme.typography.labelMedium)
            Text(question.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
            when (question.type) {
                "numerical" -> TextFieldValue(numerical, { numerical = it; storeAnswer(setOf(it)) }, "Enter numerical answer")
                else -> {
                    val options = question.optionsJson.lines().filter(String::isNotBlank)
                    options.forEachIndexed { optionIndex, option ->
                        val key = (optionIndex + 1).toString()
                        if (question.type == "multiple_correct") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = key in chosen, onCheckedChange = {
                                    storeAnswer(if (it) chosen + key else chosen - key)
                                })
                                Text(option)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = key in chosen, onClick = { storeAnswer(setOf(key)) })
                                Text(option)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = response?.markedForReview == true, onCheckedChange = { marked ->
                    vm.saveResponse(TestResponse("${attemptId}:${question.id}", attemptId, question.id,
                        response?.answerJson.orEmpty(), response?.spentMillis ?: 0L, marked))
                })
                Text("Mark for review")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { storeAnswer(emptySet()) }) { Text("Clear") }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { vm.saveAttempt(current.copy(currentIndex = (index - 1).coerceAtLeast(0))) },
                enabled = index > 0) { Text("Previous") }
            TextButton(onClick = { confirmSubmit = true }) { Text("Submit") }
            Button(onClick = { vm.saveAttempt(current.copy(currentIndex = (index + 1).coerceAtMost(questions.lastIndex))) },
                enabled = index < questions.lastIndex) { Text("Next") }
        }
    }
    if (confirmSubmit) AlertDialog(onDismissRequest = { confirmSubmit = false },
        title = { Text("Submit this test?") },
        text = { Text("${responses.count { it.answerJson.isNotBlank() }} answered · ${questions.size - responses.count { it.answerJson.isNotBlank() }} unanswered. You cannot change answers after submission.") },
        confirmButton = { TextButton(onClick = { confirmSubmit = false; finish() }) { Text("Submit test") } },
        dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Continue") } })
}

@Composable
fun ResultScreen(vm: JeeViewModel, attemptId: String) {
    val attempt by vm.attempt(attemptId).collectAsStateWithLifecycle(initialValue = null)
    val tests by vm.tests.collectAsStateWithLifecycle()
    val current = attempt
    val questions by vm.questions(current?.testId.orEmpty()).collectAsStateWithLifecycle(initialValue = emptyList())
    val responses by vm.responses(attemptId).collectAsStateWithLifecycle(initialValue = emptyList())
    val test = tests.firstOrNull { it.id == current?.testId }
    if (current == null || test == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return
    }
    val result = TestScoring.score(questions, responses.associate { it.questionId to it.answerJson.split("|").filter(String::isNotBlank) })
    val maximum = questions.sumOf { it.marks }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(test.title, style = MaterialTheme.typography.headlineSmall)
        Text("Score ${"%.2f".format(result.score)} / ${"%.2f".format(maximum)} · ${if (maximum > 0) "%.1f".format(result.score / maximum * 100) else "0"}%",
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Text("Correct ${result.correct} · Incorrect ${result.incorrect} · Partial ${result.partial} · Unanswered ${result.unanswered}")
        questions.forEachIndexed { i, q ->
            val given = responses.firstOrNull { it.questionId == q.id }?.answerJson.orEmpty()
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${i + 1}. ${q.text}", fontWeight = FontWeight.Medium)
                    Text("Your answer: ${given.ifBlank { "Not answered" }} · Correct: ${q.correctJson}")
                    if (q.explanation.isNotBlank()) Text(q.explanation,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(vm: JeeViewModel) {
    val all by vm.activities.collectAsStateWithLifecycle()
    val backlogs by vm.backlogs.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val lastWeek = (0L..6L).map { day ->
        val date = today.minusDays(6 - day)
        val (from, to) = dayBounds(date)
        date to all.filter { it.startAt < to && it.endAt > from && isStudy(it) }
            .sumOf { it.durationMillis }
    }
    val monthStart = dayBounds(today.withDayOfMonth(1)).first
    val monthly = all.filter(::isStudy).sumOf { durationWithin(it, monthStart, dayBounds(today).second) }
    val subjectValues = subjects.take(3).associateWith { subject ->
        all.filter { isStudy(it) && it.subject == subject }
            .sumOf { durationWithin(it, monthStart, dayBounds(today).second) }
    }
    val maxDay = lastWeek.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Only saved activity records contribute. Unlogged time is not estimated.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Heading("Last 7 days")
        if (all.none(::isStudy)) Text("Add study activities to start seeing real trends.")
        lastWeek.forEach { (date, millis) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(DateTimeFormatter.ofPattern("EEE")), Modifier.width(42.dp))
                LinearProgressIndicator(progress = { (millis.toFloat() / maxDay).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("${millis / 3_600_000}h ${(millis / 60_000) % 60}m", style = MaterialTheme.typography.labelSmall)
            }
        }
        Heading("This month")
        Text("Recorded study: ${monthly / 3_600_000}h ${(monthly / 60_000) % 60}m")
        Heading("Subject totals (all recorded time)")
        subjectValues.forEach { (subject, millis) ->
            Text("$subject · ${millis / 3_600_000}h ${(millis / 60_000) % 60}m",
                modifier = Modifier.padding(vertical = 4.dp))
        }
        Heading("Current preparation")
        Text("${chapters.count { it.status == "Completed" }} of ${chapters.size} chapters marked complete")
        Text("${backlogs.count { it.status == "Completed" }} backlogs completed · ${backlogs.count { it.status != "Completed" }} still open")
        Text("${tests.size} tests created")
    }
}

@Composable
fun SettingsScreen(vm: JeeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    var name by remember(settings) { mutableStateOf(current.nickname) }
    var goal by remember(settings) { mutableStateOf(current.dailyGoalMinutes.toString()) }
    var startDate by remember(settings) { mutableStateOf(dateText(current.journeyStartAt)) }
    var targetDate by remember(settings) { mutableStateOf(current.examTargetAt?.let(::dateText).orEmpty()) }
    var feedback by remember { mutableStateOf("") }
    var stagedImport by remember { mutableStateOf<String?>(null) }
    var clearDialog by remember { mutableStateOf(false) }
    var clearPhrase by remember { mutableStateOf("") }
    val backupWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { vm.exportBackup() }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } }
            }.onSuccess { feedback = "JSON backup saved." }.onFailure { feedback = "Backup failed: ${it.message}" }
        }
    }
    val csvWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val rows = buildString {
                    appendLine("title,start,end,duration_ms,subject,chapter,type,notes,source")
                    activities.sortedBy { it.startAt }.forEach { row ->
                        appendLine(listOf(row.title, dateTimeText(row.startAt), dateTimeText(row.endAt),
                            row.durationMillis.toString(), row.subject, row.chapter, row.kind, row.notes, row.source)
                            .joinToString(",") { csv(it) })
                    }
                }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(rows) } }
            }.onSuccess { feedback = "Activity CSV exported." }.onFailure { feedback = "CSV export failed: ${it.message}" }
        }
    }
    val backupReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                    ?: error("Could not read the selected file.")
            }.onSuccess { stagedImport = it; feedback = "Review the restore warning before applying the selected backup." }
                .onFailure { feedback = "Could not read the backup: ${it.message}" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Your preparation")
        TextFieldValue(name, { name = it }, "Nickname")
        TextFieldValue(goal, { goal = it.filter(Char::isDigit) }, "Daily study target (minutes)")
        TextFieldValue(startDate, { startDate = it }, "Journey start date (YYYY-MM-DD)")
        TextFieldValue(targetDate, { targetDate = it }, "Target exam date (optional, YYYY-MM-DD)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", Modifier.weight(1f))
            Switch(checked = current.darkTheme, onCheckedChange = {
                vm.putSettings(current.copy(darkTheme = it))
            })
        }
        Button(onClick = {
            val parsedStart = runCatching { LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            val parsedTarget = if (targetDate.isBlank()) null else runCatching { LocalDate.parse(targetDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            val parsedGoal = goal.toIntOrNull()
            if (parsedStart == null || (targetDate.isNotBlank() && parsedTarget == null) || parsedGoal == null || parsedGoal < 1) {
                feedback = "Check the dates and enter a daily target greater than zero."
            } else {
                vm.putSettings(current.copy(nickname = name.trim(), journeyStartAt = parsedStart,
                    examTargetAt = parsedTarget, dailyGoalMinutes = parsedGoal))
                feedback = "Settings saved."
            }
        }) { Text("Save settings") }
        HorizontalDivider()
        Heading("Your data stays on this device")
        Text("JSON backup includes locally stored study records, timer state, syllabus entries, tests, answers, and settings. This version does not store file attachments. Restoring replaces current records; validate that you chose the intended backup.")
        OutlinedButton(onClick = { backupWriter.launch("jee-command-center-backup.json") }) { Text("Export full JSON backup") }
        OutlinedButton(onClick = { backupReader.launch(arrayOf("application/json", "text/*")) }) { Text("Choose backup to restore") }
        OutlinedButton(onClick = { csvWriter.launch("jee-activities.csv") }) { Text("Export activities as CSV") }
        OutlinedButton(onClick = { clearDialog = true }) { Text("Clear all app data") }
        Text("Activities, backlogs, syllabus, tests and test answers are stored in the on-device Room database. There is no cloud sync or account.")
        if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary)
    }
    stagedImport?.let { json ->
        AlertDialog(onDismissRequest = { stagedImport = null }, title = { Text("Replace local data?") },
            text = { Text("The selected JSON backup will replace all records currently stored in this app. A malformed or unsupported backup is rejected without changing your data.") },
            confirmButton = { TextButton(onClick = {
                vm.restoreBackup(json) { result ->
                    feedback = result.fold({ "Backup restored." }, { "Restore failed: ${it.message}" })
                }
                stagedImport = null
            }) { Text("Restore backup") } },
            dismissButton = { TextButton(onClick = { stagedImport = null }) { Text("Cancel") } })
    }
    if (clearDialog) {
        AlertDialog(onDismissRequest = { clearDialog = false; clearPhrase = "" },
            title = { Text("Permanently clear all local data?") },
            text = {
                Column {
                    Text("This deletes all activities, timers, backlogs, chapters, tests, answers, and settings from this device. Export a backup first if you may need this data.")
                    TextFieldValue(clearPhrase, { clearPhrase = it }, "Type DELETE to confirm")
                }
            },
            confirmButton = { TextButton(enabled = clearPhrase == "DELETE", onClick = {
                vm.clearAllData { feedback = "All local app data cleared." }
                clearDialog = false; clearPhrase = ""
            }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { clearDialog = false; clearPhrase = "" }) { Text("Cancel") } })
    }
}

private fun dateText(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""
