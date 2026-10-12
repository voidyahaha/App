package com.jeecommandcenter.domain

import com.jeecommandcenter.data.BacklogItem
import com.jeecommandcenter.data.TestQuestion
import kotlin.math.abs

object StudyMath {
    fun timerElapsed(now: Long, segmentStart: Long, accumulated: Long, paused: Boolean): Long =
        (accumulated + if (paused) 0 else (now - segmentStart).coerceAtLeast(0)).coerceAtLeast(0)

    fun backlogCompletion(item: BacklogItem): Int {
        if (item.status == "Completed") return 100
        if (item.estimatedMinutes <= 0) return if (item.actualMinutes > 0) 100 else 0
        return ((item.actualMinutes.toDouble() / item.estimatedMinutes) * 100).toInt().coerceIn(0, 99)
    }

    fun remainingMinutes(item: BacklogItem): Int =
        (item.estimatedMinutes - item.actualMinutes).coerceAtLeast(0)
}

object TestScoring {
    data class Result(val score: Double, val correct: Int, val incorrect: Int, val partial: Int, val unanswered: Int)

    /** Answers are normalized strings; multi-select answers must be sorted before comparison. */
    fun score(questions: List<TestQuestion>, answers: Map<String, List<String>>): Result {
        var score = 0.0
        var correct = 0
        var incorrect = 0
        var partial = 0
        var unanswered = 0
        for (question in questions) {
            val given = answers[question.id].orEmpty().map(String::trim).filter(String::isNotEmpty)
            val expected = question.correctJson.split("|").map(String::trim).filter(String::isNotEmpty)
            if (given.isEmpty()) {
                unanswered++
                continue
            }
            val isCorrect = when (question.type) {
                "numerical" -> {
                    val actual = given.firstOrNull()?.toDoubleOrNull()
                    val target = expected.firstOrNull()?.toDoubleOrNull()
                    actual != null && target != null && abs(actual - target) <= question.numericTolerance
                }
                "true_false", "single_correct" -> given.firstOrNull() == expected.firstOrNull()
                else -> given.toSet() == expected.toSet()
            }
            if (isCorrect) {
                correct++
                score += question.marks
            } else if (question.type == "multiple_correct" &&
                given.all { it in expected } && expected.containsAll(given) && given.size < expected.size) {
                partial++
                score += question.partialMarks
            } else {
                incorrect++
                score -= question.negativeMarks
            }
        }
        return Result(score, correct, incorrect, partial, unanswered)
    }
}
