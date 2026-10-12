package com.jeecommandcenter.domain

import com.jeecommandcenter.data.BacklogItem
import com.jeecommandcenter.data.TestQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyMathTest {
    @Test fun runningTimerIncludesElapsedSegment() {
        assertEquals(11_500L, StudyMath.timerElapsed(6_400L, 1_000L, 6_100L, false))
    }

    @Test fun pausedTimerDoesNotContinueAdvancing() {
        assertEquals(90_000L, StudyMath.timerElapsed(9_000_000L, 2L, 90_000L, true))
    }

    @Test fun elapsedTimerNeverBecomesNegativeWhenClockMovesBackwards() {
        assertEquals(0L, StudyMath.timerElapsed(500L, 1_000L, 0L, false))
    }

    @Test fun backlogProgressIsClampedUntilExplicitlyCompleted() {
        val item = backlog(estimated = 100, actual = 170)
        assertEquals(99, StudyMath.backlogCompletion(item))
        assertEquals(100, StudyMath.backlogCompletion(item.copy(status = "Completed")))
        assertEquals(0, StudyMath.remainingMinutes(item))
    }

    @Test fun workloadWithoutEstimateIsNotAutomaticallyComplete() {
        assertEquals(0, StudyMath.backlogCompletion(backlog(0, 0)))
        assertEquals(100, StudyMath.backlogCompletion(backlog(0, 2)))
    }

    private fun backlog(estimated: Int, actual: Int) = BacklogItem(
        id = "b", title = "Review", subject = "Physics", chapter = "", type = "Revision",
        priority = "Medium", difficulty = 3, deadlineAt = null, estimatedMinutes = estimated,
        actualMinutes = actual, status = "In progress", notes = "", parentId = null,
        createdAt = 1L, updatedAt = 1L
    )
}

class TestScoringTest {
    @Test fun appliesConfiguredPositiveNegativeAndPartialMarks() {
        val questions = listOf(
            question("right", "single_correct", "2", marks = 4.0, negative = 1.0),
            question("wrong", "single_correct", "2", marks = 4.0, negative = 1.0),
            question("part", "multiple_correct", "1|3", marks = 4.0, partial = 1.5),
            question("blank", "numerical", "3.14", marks = 2.0, negative = 0.5)
        )
        val result = TestScoring.score(questions, mapOf(
            "right" to listOf("2"), "wrong" to listOf("1"), "part" to listOf("1")
        ))
        assertEquals(4.5, result.score, 0.001)
        assertEquals(1, result.correct)
        assertEquals(1, result.incorrect)
        assertEquals(1, result.partial)
        assertEquals(1, result.unanswered)
    }

    @Test fun numericalScoringUsesConfiguredTolerance() {
        val question = question("n", "numerical", "3.14", tolerance = 0.02)
        assertEquals(2.0, TestScoring.score(listOf(question), mapOf("n" to listOf("3.15"))).score, 0.001)
        assertEquals(-0.5, TestScoring.score(listOf(question), mapOf("n" to listOf("3.2"))).score, 0.001)
    }

    @Test fun multiSelectWithWrongExtraAnswerIsNotPartialCredit() {
        val question = question("m", "multiple_correct", "1|2", marks = 4.0, negative = 1.0, partial = 1.0)
        val result = TestScoring.score(listOf(question), mapOf("m" to listOf("1", "3")))
        assertEquals(-1.0, result.score, 0.001)
        assertEquals(0, result.partial)
        assertEquals(1, result.incorrect)
    }

    private fun question(
        id: String,
        type: String,
        answer: String,
        marks: Double = 2.0,
        negative: Double = 0.5,
        partial: Double = 0.0,
        tolerance: Double = 0.0
    ) = TestQuestion(id, "test", 0, type, "Question", "A\nB\nC",
        answer, tolerance, "", "Physics", "", marks, negative, partial)
}
