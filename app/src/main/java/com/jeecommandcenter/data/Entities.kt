package com.jeecommandcenter.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "activities")
data class ActivityRecord(
    @PrimaryKey val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val durationMillis: Long,
    val subject: String,
    val chapter: String,
    val kind: String,
    val notes: String,
    val source: String,
    val productivity: Int,
    val backlogId: String?
)

@Serializable
@Entity(tableName = "study_timer")
data class StudyTimer(
    @PrimaryKey val id: String = "active",
    val title: String,
    val originalStartAt: Long,
    val segmentStartAt: Long,
    val accumulatedMillis: Long,
    val paused: Boolean,
    val subject: String,
    val chapter: String,
    val backlogId: String?
)

@Serializable
@Entity(tableName = "backlogs")
data class BacklogItem(
    @PrimaryKey val id: String,
    val title: String,
    val subject: String,
    val chapter: String,
    val type: String,
    val priority: String,
    val difficulty: Int,
    val deadlineAt: Long?,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val status: String,
    val notes: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey val id: String,
    val subject: String,
    val schoolClass: Int,
    val title: String,
    val status: String,
    val lecturesDone: Boolean,
    val notesDone: Boolean,
    val practiceDone: Boolean,
    val pyqDone: Boolean,
    val confidence: Int,
    val difficulty: Int,
    val lastStudiedAt: Long?,
    val revisionDueAt: Long?,
    val personalNotes: String
)

@Serializable
@Entity(tableName = "tests")
data class PracticeTest(
    @PrimaryKey val id: String,
    val title: String,
    val durationMinutes: Int,
    val createdAt: Long,
    val instructions: String
)

@Serializable
@Entity(tableName = "questions")
data class TestQuestion(
    @PrimaryKey val id: String,
    val testId: String,
    val position: Int,
    val type: String,
    val text: String,
    val optionsJson: String,
    val correctJson: String,
    val numericTolerance: Double,
    val explanation: String,
    val subject: String,
    val chapter: String,
    val marks: Double,
    val negativeMarks: Double,
    val partialMarks: Double
)

@Serializable
@Entity(tableName = "attempts")
data class TestAttempt(
    @PrimaryKey val id: String,
    val testId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val currentIndex: Int,
    val status: String
)

@Serializable
@Entity(tableName = "responses")
data class TestResponse(
    @PrimaryKey val id: String,
    val attemptId: String,
    val questionId: String,
    val answerJson: String,
    val spentMillis: Long,
    val markedForReview: Boolean
)

@Serializable
@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: String = "settings",
    val nickname: String = "",
    val journeyStartAt: Long = System.currentTimeMillis(),
    val examTargetAt: Long? = null,
    val dailyGoalMinutes: Int = 360,
    val darkTheme: Boolean = true
)
