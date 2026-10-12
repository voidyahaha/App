package com.jeecommandcenter.data

import androidx.room.withTransaction
import com.jeecommandcenter.domain.StudyMath
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@Serializable
data class BackupEnvelope(
    val format: String = "jee-command-center-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val activities: List<ActivityRecord>,
    val timer: StudyTimer? = null,
    val backlogs: List<BacklogItem>,
    val chapters: List<Chapter>,
    val tests: List<PracticeTest>,
    val questions: List<TestQuestion>,
    val attempts: List<TestAttempt>,
    val responses: List<TestResponse>,
    val settings: AppSettings?
)

class StudyRepository(private val db: AppDatabase) {
    private val dao = db.studyDao()
    private val timerMutex = Mutex()
    val activities: Flow<List<ActivityRecord>> = dao.activities()
    val timer: Flow<StudyTimer?> = dao.timer()
    val backlogs: Flow<List<BacklogItem>> = dao.backlogs()
    val chapters: Flow<List<Chapter>> = dao.chapters()
    val tests: Flow<List<PracticeTest>> = dao.tests()
    val settings: Flow<AppSettings?> = dao.settings()

    fun activitiesBetween(from: Long, to: Long) = dao.activitiesBetween(from, to)
    fun questions(testId: String) = dao.questions(testId)
    fun attempt(id: String) = dao.attemptFlow(id)
    fun responses(attemptId: String) = dao.responses(attemptId)
    fun attempts(testId: String) = dao.attempts(testId)

    suspend fun addActivity(activity: ActivityRecord) = dao.putActivity(activity)
    suspend fun deleteActivity(id: String) = dao.deleteActivity(id)
    suspend fun addBacklog(item: BacklogItem) = dao.putBacklog(item)
    suspend fun deleteBacklog(id: String) = dao.deleteBacklog(id)
    suspend fun addChapter(chapter: Chapter) = dao.putChapter(chapter)
    suspend fun deleteChapter(id: String) = dao.deleteChapter(id)
    suspend fun addTest(test: PracticeTest) = dao.putTest(test)
    suspend fun saveSettings(settings: AppSettings) = dao.putSettings(settings)
    suspend fun addQuestion(question: TestQuestion) =
        dao.putQuestion(question.copy(position = dao.questionCount(question.testId)))
    suspend fun saveAttempt(attempt: TestAttempt) = dao.putAttempt(attempt)
    suspend fun saveResponse(response: TestResponse) = dao.putResponse(response)

    suspend fun seedChaptersIfEmpty() {
        if (dao.chapterCount() == 0) {
            dao.putChapters(SyllabusCatalog.chapters.map { (subject, level, title) ->
                Chapter(
                    id = UUID.randomUUID().toString(), subject = subject, schoolClass = level,
                    title = title, status = "Not started", lecturesDone = false,
                    notesDone = false, practiceDone = false, pyqDone = false,
                    confidence = 0, difficulty = 3, lastStudiedAt = null,
                    revisionDueAt = null, personalNotes = ""
                )
            })
        }
    }

    suspend fun startTimer(title: String, subject: String, chapter: String, backlogId: String?) {
        timerMutex.withLock {
            check(dao.activeTimer() == null) { "A study timer is already active." }
            val now = System.currentTimeMillis()
            dao.putTimer(StudyTimer(title = title, originalStartAt = now, segmentStartAt = now,
                accumulatedMillis = 0L, paused = false, subject = subject,
                chapter = chapter, backlogId = backlogId))
        }
    }

    suspend fun pauseTimer() {
        val current = dao.activeTimer() ?: return
        if (!current.paused) {
            val now = System.currentTimeMillis()
            val segment = (now - current.segmentStartAt).coerceAtLeast(0)
            if (segment > 0) saveTimerSegment(current, current.segmentStartAt, now, segment)
            dao.putTimer(current.copy(accumulatedMillis = current.accumulatedMillis + segment, paused = true))
        }
    }

    suspend fun resumeTimer() {
        val current = dao.activeTimer() ?: return
        if (current.paused) dao.putTimer(current.copy(segmentStartAt = System.currentTimeMillis(), paused = false))
    }

    suspend fun stopTimer() {
        val current = dao.activeTimer() ?: return
        val now = System.currentTimeMillis()
        val elapsed = StudyMath.timerElapsed(now, current.segmentStartAt,
            current.accumulatedMillis, current.paused)
        if (!current.paused) {
            val segment = (now - current.segmentStartAt).coerceAtLeast(0)
            if (segment > 0) saveTimerSegment(current, current.segmentStartAt, now, segment)
        }
        if (elapsed > 0L) {
            current.backlogId?.let { id ->
                backlogsValue(id)?.let { backlog ->
                    val minutes = (elapsed / 60_000L).toInt()
                    dao.putBacklog(backlog.copy(
                        actualMinutes = backlog.actualMinutes + minutes,
                        status = if (backlog.status == "Not started") "In progress" else backlog.status,
                        updatedAt = now
                    ))
                }
            }
        }
        dao.clearTimer()
    }

    private suspend fun saveTimerSegment(timer: StudyTimer, start: Long, end: Long, duration: Long) {
        dao.putActivity(ActivityRecord(
            id = UUID.randomUUID().toString(), title = timer.title,
            startAt = start, endAt = end, durationMillis = duration,
            subject = timer.subject, chapter = timer.chapter, kind = "Study",
            notes = "", source = "Timer", productivity = 0, backlogId = timer.backlogId
        ))
    }

    private suspend fun backlogsValue(id: String): BacklogItem? =
        dao.backlogsSnapshot().firstOrNull { it.id == id }

    suspend fun createAttempt(testId: String): String {
        val existing = dao.resumableAttempt()
        if (existing != null && existing.testId == testId) return existing.id
        val id = UUID.randomUUID().toString()
        dao.putAttempt(TestAttempt(id, testId, System.currentTimeMillis(), null, 0, "in_progress"))
        return id
    }

    suspend fun exportBackup(): String {
        val envelope = BackupEnvelope(
            exportedAt = System.currentTimeMillis(),
            activities = dao.activitiesSnapshot(),
            timer = dao.activeTimer(),
            backlogs = dao.backlogsSnapshot(),
            chapters = dao.chaptersSnapshot(),
            tests = dao.allTests(),
            questions = dao.allQuestions(),
            attempts = dao.allAttempts(),
            responses = dao.allResponses(),
            settings = dao.settingsNow()
        )
        return Json { prettyPrint = true }.encodeToString(BackupEnvelope.serializer(), envelope)
    }

    suspend fun restoreBackup(json: String) {
        val parsed = Json { ignoreUnknownKeys = false }.decodeFromString(BackupEnvelope.serializer(), json)
        require(parsed.format == "jee-command-center-backup" && parsed.version == 1) {
            "This backup format is not supported."
        }
        require(parsed.activities.all { it.endAt >= it.startAt && it.durationMillis >= 0 }) {
            "Backup contains an invalid activity time range."
        }
        require(parsed.questions.all { it.marks >= 0 && it.negativeMarks >= 0 && it.partialMarks >= 0 }) {
            "Backup contains invalid test marking rules."
        }
        db.withTransaction {
            dao.clearActivities(); dao.clearTimer(); dao.clearBacklogs(); dao.clearChapters()
            dao.clearResponses(); dao.clearAttempts(); dao.clearQuestions(); dao.clearTests(); dao.clearSettings()
            dao.putActivities(parsed.activities)
            parsed.timer?.let { dao.putTimer(it) }
            dao.putBacklogs(parsed.backlogs)
            dao.putChapters(parsed.chapters)
            dao.putTests(parsed.tests)
            dao.putQuestions(parsed.questions)
            dao.putAttempts(parsed.attempts)
            dao.putResponses(parsed.responses)
            parsed.settings?.let { dao.putSettings(it) }
        }
    }

    suspend fun clearAll() {
        db.withTransaction {
            dao.clearActivities(); dao.clearTimer(); dao.clearBacklogs(); dao.clearChapters()
            dao.clearResponses(); dao.clearAttempts(); dao.clearQuestions(); dao.clearTests(); dao.clearSettings()
        }
    }
}

private suspend fun StudyDao.activitiesSnapshot(): List<ActivityRecord> = allActivitiesSnapshot()
