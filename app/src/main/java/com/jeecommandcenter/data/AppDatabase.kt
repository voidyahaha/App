package com.jeecommandcenter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM activities ORDER BY startAt DESC")
    fun activities(): Flow<List<ActivityRecord>>

    @Query("SELECT * FROM activities ORDER BY startAt DESC")
    suspend fun allActivitiesSnapshot(): List<ActivityRecord>

    @Query("SELECT * FROM activities WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    fun activitiesBetween(from: Long, to: Long): Flow<List<ActivityRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putActivity(item: ActivityRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putActivities(items: List<ActivityRecord>)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivity(id: String)

    @Query("DELETE FROM activities")
    suspend fun clearActivities()

    @Query("SELECT * FROM study_timer WHERE id = 'active' LIMIT 1")
    fun timer(): Flow<StudyTimer?>

    @Query("SELECT * FROM study_timer WHERE id = 'active' LIMIT 1")
    suspend fun activeTimer(): StudyTimer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTimer(timer: StudyTimer)

    @Query("DELETE FROM study_timer")
    suspend fun clearTimer()

    @Query("SELECT * FROM backlogs ORDER BY CASE priority WHEN 'Critical' THEN 0 WHEN 'High' THEN 1 WHEN 'Medium' THEN 2 ELSE 3 END, deadlineAt")
    fun backlogs(): Flow<List<BacklogItem>>

    @Query("SELECT * FROM backlogs")
    suspend fun backlogsSnapshot(): List<BacklogItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBacklog(item: BacklogItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBacklogs(items: List<BacklogItem>)

    @Query("DELETE FROM backlogs WHERE id = :id")
    suspend fun deleteBacklog(id: String)

    @Query("DELETE FROM backlogs")
    suspend fun clearBacklogs()

    @Query("SELECT * FROM chapters ORDER BY subject, schoolClass, title")
    fun chapters(): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters")
    suspend fun chaptersSnapshot(): List<Chapter>

    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun chapterCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChapter(item: Chapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChapters(items: List<Chapter>)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapter(id: String)

    @Query("DELETE FROM chapters")
    suspend fun clearChapters()

    @Query("SELECT * FROM tests ORDER BY createdAt DESC")
    fun tests(): Flow<List<PracticeTest>>

    @Query("SELECT * FROM tests")
    suspend fun allTests(): List<PracticeTest>

    @Query("DELETE FROM tests")
    suspend fun clearTests()

    @Query("SELECT * FROM tests WHERE id = :id LIMIT 1")
    suspend fun test(id: String): PracticeTest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTest(test: PracticeTest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTests(tests: List<PracticeTest>)

    @Query("SELECT * FROM questions WHERE testId = :testId ORDER BY position")
    fun questions(testId: String): Flow<List<TestQuestion>>

    @Query("SELECT * FROM questions WHERE testId = :testId ORDER BY position")
    suspend fun questionsNow(testId: String): List<TestQuestion>

    @Query("SELECT COUNT(*) FROM questions WHERE testId = :testId")
    suspend fun questionCount(testId: String): Int

    @Query("SELECT * FROM questions")
    suspend fun allQuestions(): List<TestQuestion>

    @Query("DELETE FROM questions")
    suspend fun clearQuestions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQuestion(question: TestQuestion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQuestions(questions: List<TestQuestion>)

    @Query("SELECT * FROM attempts WHERE id = :id LIMIT 1")
    suspend fun attempt(id: String): TestAttempt?

    @Query("SELECT * FROM attempts WHERE id = :id LIMIT 1")
    fun attemptFlow(id: String): Flow<TestAttempt?>

    @Query("SELECT * FROM attempts")
    suspend fun allAttempts(): List<TestAttempt>

    @Query("DELETE FROM attempts")
    suspend fun clearAttempts()

    @Query("SELECT * FROM attempts WHERE testId = :testId ORDER BY startedAt DESC")
    fun attempts(testId: String): Flow<List<TestAttempt>>

    @Query("SELECT * FROM attempts WHERE status = 'in_progress' ORDER BY startedAt DESC LIMIT 1")
    suspend fun resumableAttempt(): TestAttempt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAttempt(attempt: TestAttempt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAttempts(attempts: List<TestAttempt>)

    @Query("SELECT * FROM responses WHERE attemptId = :attemptId")
    fun responses(attemptId: String): Flow<List<TestResponse>>

    @Query("SELECT * FROM responses WHERE attemptId = :attemptId")
    suspend fun responsesNow(attemptId: String): List<TestResponse>

    @Query("SELECT * FROM responses")
    suspend fun allResponses(): List<TestResponse>

    @Query("DELETE FROM responses")
    suspend fun clearResponses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResponse(response: TestResponse)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResponses(responses: List<TestResponse>)

    @Query("SELECT * FROM settings WHERE id = 'settings' LIMIT 1")
    fun settings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 'settings' LIMIT 1")
    suspend fun settingsNow(): AppSettings?

    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(settings: AppSettings)
}

@Database(
    entities = [
        ActivityRecord::class, StudyTimer::class, BacklogItem::class, Chapter::class,
        PracticeTest::class, TestQuestion::class, TestAttempt::class, TestResponse::class,
        AppSettings::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "jee-command-center.db"
            ).build().also { instance = it }
        }
    }
}
