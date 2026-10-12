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
    LocalDateTime.parse(dateTime.trim(), dateTimeFormat)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun dateTimeText(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()
    .format(dateTimeFormat)

private fun dayBounds(day: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return day.atStartOfDay(zone).toInstant().toEpochMilli() to
        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

private fun todayActivities(all: List<ActivityRecord>): List<ActivityRecord> {
    val (from, to) = dayBounds(LocalDate.now())
    return all.filter { it.startAt < to && it.endAt > from }
}

private fun isStudy(activity: ActivityRecord) =
    activity.kind in setOf(
        "Study", "Lecture", "Practice", "Revision",
        "Test", "Assignment"
    )

private fun durationWithin(
    activity: ActivityRecord,
    from: Long,
    to: Long
): Long {
    val interval = (activity.endAt - activity.startAt).coerceAtLeast(0L)
    if (interval == 0L) return 0L

    val overlap = (
        minOf(activity.endAt, to) -
            maxOf(activity.startAt, from)
        ).coerceAtLeast(0L)

    return (activity.durationMillis.toDouble() * overlap / interval).toLong()
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextFieldValue(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine
    )
}

/*
 * FIX:
 * modifier comes before onSelect.
 * This allows trailing lambdas such as { subject = it }
 * to bind to onSelect instead of Modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    value: String,
    values: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    vm: JeeViewModel,
    onAddActivity: () -> Unit,
    onAddBacklog: () -> Unit,
    onTests: () -> Unit,
    onAnalytics: () -> Unit
) {
    val all by vm.activities.collectAsStateWithLifecycle()
    val backlogs by vm.backlogs.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val today = todayActivities(all)
    val (dayStart, dayEnd) = dayBounds(LocalDate.now())

    val study = today.filter(::isStudy)
        .sumOf { durationWithin(it, dayStart, dayEnd) }

    var timerForm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val goal = settings?.dailyGoalMinutes ?: 360

    val weekHours = (0L..6L).sumOf { day ->
        val (from, to) = dayBounds(LocalDate.now().minusDays(6 - day))
        all.filter(::isStudy).sumOf {
            durationWithin(it, from, to)
        }
    } / 3_600_000.0

    val streak = remember(all) {
        val days = all.filter(::isStudy).map {
            Instant.ofEpochMilli(it.startAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSet()

        var cursor = LocalDate.now()
        if (cursor !in days) cursor = cursor.minusDays(1)

        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        count
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val journeyDay = (
            (
                dayStart - (settings?.journeyStartAt ?: dayStart)
                ) / 86_400_000L + 1
            ).coerceAtLeast(1)

        Text(
            "${settings?.nickname?.takeIf(String::isNotBlank)?.let { "$it · " } ?: ""}Day $journeyDay of your preparation",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Metric(
                "Study today",
                "${study / 3_600_000}h ${(study / 60_000 % 60)}m",
                Modifier.weight(1f)
            )
            Metric("Target", "$goal min", Modifier.weight(1f))
        }

        LinearProgressIndicator(
            progress = {
                (study / 60_000.0 / goal.coerceAtLeast(1))
                    .toFloat().coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Metric("This week", "%.1f h".format(weekHours), Modifier.weight(1f))
            Metric("Study streak", "$streak days", Modifier.weight(1f))
        }

        Heading("Today by subject")

        subjects.take(3).forEach { subject ->
            val minutes = today.filter {
                isStudy(it) && it.subject == subject
            }.sumOf {
                durationWithin(it, dayStart, dayEnd)
            } / 60_000

            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(subject)
                Text(
                    "$minutes min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Heading("Preparation overview")

        val completed = chapters.count { it.status == "Completed" }
        Text("Chapters: $completed completed · ${chapters.size - completed} remaining")

        val openBacklogs = backlogs.filter { it.status != "Completed" }
        Text(
            "Backlogs: ${openBacklogs.size} open · " +
                "${openBacklogs.count { it.deadlineAt != null && it.deadlineAt < System.currentTimeMillis() }} overdue"
        )

        Heading("Quick actions")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { timerForm = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (timer == null) "Start study" else "Timer active")
            }

            OutlinedButton(
                onClick = onAddActivity,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add activity")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddBacklog,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add backlog")
            }

            OutlinedButton(
                onClick = onTests,
                modifier = Modifier.weight(1f)
            ) {
                Text("Create test")
            }
        }

        OutlinedButton(
            onClick = onAnalytics,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View study analytics")
        }

        Heading("Recent activities")

        if (today.isEmpty()) {
            Text(
                "No activities recorded today. Only saved entries appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        today.sortedByDescending { it.startAt }
            .take(5)
            .forEach { ActivityRow(it) }

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }

    if (timerForm) {
        TimerDialog(
            vm,
            onError = { message = it },
            onClose = { timerForm = false }
        )
    }
}

@Composable
private fun ActivityRow(
    activity: ActivityRecord,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(activity.title, fontWeight = FontWeight.Medium)

                Text(
                    "${activity.subject} · ${activity.kind} · " +
                        "${com.jeecommandcenter.formatTime(activity.startAt)}–" +
                        "${com.jeecommandcenter.formatTime(activity.endAt)}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Recorded duration ${com.jeecommandcenter.formatDuration(activity.durationMillis)} · ${activity.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            trailing?.invoke()
        }
    }
}

@Composable
private fun TimerDialog(
    vm: JeeViewModel,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    backlog: BacklogItem? = null
) {
    var title by remember { mutableStateOf(backlog?.title ?: "") }
    var subject by remember { mutableStateOf(backlog?.subject ?: "Physics") }
    var chapter by remember { mutableStateOf(backlog?.chapter ?: "") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Start a study session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldValue(title, { title = it }, "Session title")
                SelectField(subject, subjects, "Subject") { subject = it }
                TextFieldValue(chapter, { chapter = it }, "Chapter (optional)")

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    error = "Add a session title."
                    return@TextButton
                }

                vm.startTimer(
                    title, subject, chapter, backlog?.id,
                    { error = it; onError(it) }
                )
                onClose()
            }) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    )
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
    val entries = all.filter {
        it.startAt < end && it.endAt > start
    }.sortedBy { it.startAt }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { selectedDay = selectedDay.minusDays(1) }) {
                Text("‹ Previous")
            }

            Text(
                selectedDay.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                fontWeight = FontWeight.SemiBold
            )

            TextButton(onClick = { selectedDay = selectedDay.plusDays(1) }) {
                Text("Next ›")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text("Add activity") }

            OutlinedButton(
                onClick = { showTimer = true },
                enabled = timer == null
            ) {
                Text("Start timer")
            }
        }

        if (timer != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (timer!!.paused) "Session paused" else "Timer is running",
                    Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        if (timer!!.paused) vm.resumeTimer()
                        else vm.pauseTimer()
                    }
                ) {
                    Text(if (timer!!.paused) "Resume" else "Pause")
                }

                TextButton(onClick = { confirmStop = true }) {
                    Text("Stop & save")
                }
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Heading("Recorded timeline")

        Text(
            "Entries retain their recorded source. Empty time is unrecorded, not estimated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (entries.isEmpty()) Text("Nothing recorded for this date.")

        LazyColumn {
            var cursor = start

            entries.forEach { entry ->
                val clippedStart = maxOf(start, entry.startAt)
                val clippedEnd = minOf(end, entry.endAt)

                if (clippedStart > cursor) {
                    item(key = "gap-$cursor") {
                        Text(
                            "Unrecorded · ${com.jeecommandcenter.formatTime(cursor)}–${com.jeecommandcenter.formatTime(clippedStart)}",
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item(key = entry.id) {
                    ActivityRow(entry) {
                        TextButton(onClick = { vm.deleteActivity(entry.id) }) {
                            Text("Delete")
                        }
                    }
                }

                cursor = maxOf(cursor, clippedEnd)
            }

            if (cursor < end && entries.isNotEmpty()) {
                item {
                    Text(
                        "Unrecorded · ${com.jeecommandcenter.formatTime(cursor)}–23:59:59",
                        Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAdd) {
        ActivityDialog(vm, selectedDay, { showAdd = false }, { error = it })
    }

    if (showTimer) {
        TimerDialog(vm, { error = it }, { showTimer = false })
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Save this study session?") },
            text = {
                Text(
                    "Only active timer time is recorded. Stopping does not mark a linked backlog complete."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopTimer()
                    confirmStop = false
                }) {
                    Text("Stop and save")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text("Keep running")
                }
            }
        )
    }
}

@Composable
private fun ActivityDialog(
    vm: JeeViewModel,
    day: LocalDate,
    onClose: () -> Unit,
    onError: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val dayStart = day.atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    val initialStart = if (day == LocalDate.now()) {
        maxOf(now - 3_600_000, dayStart)
    } else {
        dayStart + 9 * 3_600_000
    }

    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(dateTimeText(initialStart)) }
    var end by remember { mutableStateOf(dateTimeText(initialStart + 3_600_000)) }
    var subject by remember { mutableStateOf("Physics") }
    var chapter by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Study") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add an activity") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextFieldValue(title, { title = it }, "What did you do?")

                Text(
                    "Use local time in YYYY-MM-DD HH:mm:ss format.",
                    style = MaterialTheme.typography.labelSmall
                )

                TextFieldValue(start, { start = it }, "Start date and time")
                TextFieldValue(end, { end = it }, "End date and time")

                SelectField(subject, subjects, "Subject") { subject = it }

                TextFieldValue(chapter, { chapter = it }, "Chapter or topic")

                SelectField(
                    kind,
                    listOf(
                        "Study", "Lecture", "Practice", "Revision",
                        "Test", "Assignment", "Break", "Personal", "Other"
                    ),
                    "Activity type"
                ) {
                    kind = it
                }

                TextFieldValue(notes, { notes = it }, "Notes", singleLine = false)

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = localMillis(start)
                val e = localMillis(end)

                if (s == null || e == null) {
                    error = "Enter valid date and time values."
                    return@TextButton
                }

                vm.addActivity(title, s, e, subject, chapter, kind, notes) {
                    error = it
                    onError(it)
                }

                onClose()
            }) {
                Text("Save activity")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    )
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

    val visible = items.filter {
        it.title.contains(query, true) || it.chapter.contains(query, true)
    }.filter {
        status == "All" ||
            (if (status == "Open") it.status != "Completed"
            else it.status == "Completed")
    }

    val remaining = visible.filter { it.status != "Completed" }
        .sumOf(StudyMath::remainingMinutes)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectField(
                status,
                listOf("Open", "Completed", "All"),
                "Show",
                modifier = Modifier.weight(1f),
                onSelect = { status = it }
            )

            Button(onClick = {
                editing = null
                add = true
            }) {
                Text("Add")
            }
        }

        TextFieldValue(query, { query = it }, "Search backlogs")

        Text(
            "${visible.count { it.status != "Completed" }} items · " +
                "${remaining / 60}h ${remaining % 60}m estimated remaining",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (visible.isEmpty()) {
            Text("No matching backlog items. Add a task to start tracking work.")
        }

        LazyColumn {
            items(visible, key = { it.id }) { item ->
                val overdue = item.deadlineAt?.let {
                    it < System.currentTimeMillis() && item.status != "Completed"
                } == true

                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold)

                                Text(
                                    "${item.subject} · ${item.chapter.ifBlank { "No chapter" }} · ${item.type}",
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                            }) {
                                Text(item.status)
                            }
                        }

                        LinearProgressIndicator(
                            progress = { StudyMath.backlogCompletion(item) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )

                        Text(
                            "${StudyMath.backlogCompletion(item)}% · " +
                                "${item.actualMinutes}/${item.estimatedMinutes} min · " +
                                "${item.priority} priority" +
                                if (overdue) " · OVERDUE" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row {
                            TextButton(onClick = {
                                timerItem = item
                                showTimer = true
                            }) {
                                Text("Study")
                            }

                            TextButton(onClick = {
                                editing = item
                                add = true
                            }) {
                                Text("Edit")
                            }

                            TextButton(onClick = {
                                val now = System.currentTimeMillis()

                                vm.updateBacklog(
                                    item.copy(
                                        id = UUID.randomUUID().toString(),
                                        title = "${item.title} (copy)",
                                        status = "Not started",
                                        actualMinutes = 0,
                                        parentId = item.id,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                )
                            }) {
                                Text("Duplicate")
                            }

                            TextButton(onClick = { vm.deleteBacklog(item.id) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }

    if (add) {
        BacklogDialog(vm, editing, { add = false }, { message = it })
    }

    if (showTimer && timerItem != null) {
        TimerDialog(vm, { message = it }, { showTimer = false }, timerItem)
    }
}

@Composable
private fun BacklogDialog(
    vm: JeeViewModel,
    initial: BacklogItem?,
    onClose: () -> Unit,
    onError: (String) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var subject by remember(initial) { mutableStateOf(initial?.subject ?: "Physics") }
    var chapter by remember(initial) { mutableStateOf(initial?.chapter ?: "") }
    var type by remember(initial) { mutableStateOf(initial?.type ?: "Practice") }
    var priority by remember(initial) { mutableStateOf(initial?.priority ?: "Medium") }
    var estimate by remember(initial) {
        mutableStateOf((initial?.estimatedMinutes ?: 60).toString())
    }
    var deadline by remember(initial) {
        mutableStateOf(initial?.deadlineAt?.let(::dateTimeText) ?: "")
    }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial == null) "Add backlog" else "Edit backlog") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextFieldValue(title, { title = it }, "Task")
                SelectField(subject, subjects, "Subject") { subject = it }
                TextFieldValue(chapter, { chapter = it }, "Chapter or topic")

                SelectField(
                    type,
                    listOf(
                        "Lecture", "Notes", "DPP", "Module", "PYQ",
                        "Revision", "Assignment", "Test", "Custom"
                    ),
                    "Type"
                ) {
                    type = it
                }

                SelectField(
                    priority,
                    listOf("Low", "Medium", "High", "Critical"),
                    "Priority"
                ) {
                    priority = it
                }

                TextFieldValue(
                    estimate,
                    { estimate = it.filter(Char::isDigit) },
                    "Estimated minutes"
                )

                TextFieldValue(
                    deadline,
                    { deadline = it },
                    "Deadline (YYYY-MM-DD HH:mm:ss; optional)"
                )

                TextFieldValue(notes, { notes = it }, "Notes", singleLine = false)

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = estimate.toIntOrNull()

                if (title.isBlank() || minutes == null || minutes < 0) {
                    error = "Add a title and valid estimated minutes."
                    return@TextButton
                }

                val due = if (deadline.isBlank()) null else localMillis(deadline)

                if (deadline.isNotBlank() && due == null) {
                    error = "Enter a valid deadline."
                    return@TextButton
                }

                if (initial == null) {
                    vm.addBacklog(
                        title, subject, chapter, type, priority,
                        minutes, due, notes
                    ) {
                        error = it
                        onError(it)
                    }
                } else {
                    vm.updateBacklog(
                        initial.copy(
                            title = title.trim(),
                            subject = subject,
                            chapter = chapter,
                            type = type,
                            priority = priority,
                            estimatedMinutes = minutes,
                            deadlineAt = due,
                            notes = notes
                        )
                    )
                }

                onClose()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    )
}

@Composable
fun SyllabusScreen(vm: JeeViewModel) {
    val chapters by vm.chapters.collectAsStateWithLifecycle()

    var subject by remember { mutableStateOf("Physics") }
    var level by remember { mutableIntStateOf(11) }
    var add by remember { mutableStateOf(false) }

    val filtered = chapters.filter {
        it.subject == subject && it.schoolClass == level
    }

    val completed = filtered.count { it.status == "Completed" }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(
                subject,
                subjects.take(3),
                "Subject",
                modifier = Modifier.weight(1f)
            ) {
                subject = it
            }

            SelectField(
                "Class $level",
                listOf("Class 11", "Class 12"),
                "Class",
                modifier = Modifier.weight(1f)
            ) {
                level = if (it.endsWith("11")) 11 else 12
            }
        }

        LinearProgressIndicator(
            progress = {
                if (filtered.isEmpty()) 0f
                else completed.toFloat() / filtered.size
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Text(
            "$completed of ${filtered.size} chapters complete · ${filtered.size - completed} remaining",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(onClick = { add = true }) {
            Text("Add custom chapter")
        }

        Text(
            "Starter chapter list is editable; verify it against your current exam syllabus.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn {
            items(filtered, key = { it.id }) { chapter ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, fontWeight = FontWeight.Medium)

                            Text(
                                "Confidence ${chapter.confidence}/5 · difficulty ${chapter.difficulty}/5",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        TextButton(onClick = {
                            val next = when (chapter.status) {
                                "Not started" -> "Studying"
                                "Studying" -> "Revision needed"
                                "Revision needed" -> "Completed"
                                else -> "Not started"
                            }

                            vm.updateChapter(
                                chapter.copy(
                                    status = next,
                                    lastStudiedAt = System.currentTimeMillis()
                                )
                            )
                        }) {
                            Text(chapter.status)
                        }

                        TextButton(onClick = { vm.deleteChapter(chapter.id) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }

    if (add) {
        AddChapterDialog(vm, { add = false })
    }
}

@Composable
private fun AddChapterDialog(vm: JeeViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Physics") }
    var level by remember { mutableIntStateOf(11) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldValue(title, { title = it }, "Chapter name")

                SelectField(subject, subjects.take(3), "Subject") {
                    subject = it
                }

                SelectField("Class $level", listOf("Class 11", "Class 12"), "Class") {
                    level = if (it.endsWith("11")) 11 else 12
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    vm.updateChapter(
                        Chapter(
                            UUID.randomUUID().toString(),
                            subject,
                            level,
                            title.trim(),
                            "Not started",
                            false,
                            false,
                            false,
                            false,
                            0,
                            3,
                            null,
                            null,
                            ""
                        )
                    )
                }
                onClose()
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    )
}

@Composable
fun TestsScreen(
    vm: JeeViewModel,
    onAttempt: (String) -> Unit,
    onResult: (String) -> Unit
) {
    val tests by vm.tests.collectAsStateWithLifecycle()

    var create by remember { mutableStateOf(false) }
    var questionFor by remember { mutableStateOf<PracticeTest?>(null) }
    var error by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Create a test, add your own questions and marking rules, then take it offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { create = true },
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text("Create test")
        }

        if (tests.isEmpty()) {
            Text("No tests yet. Create a test to add questions and start an attempt.")
        }

        LazyColumn {
            items(tests, key = { it.id }) { test ->
                val questions by vm.questions(test.id)
                    .
