package com.jeecommandcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jeecommandcenter.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class JeeViewModel(val repo: StudyRepository) : ViewModel() {
    val activities = repo.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val timer = repo.timer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val backlogs = repo.backlogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chapters = repo.chapters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tests = repo.tests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { viewModelScope.launch { repo.seedChaptersIfEmpty() } }

    fun addActivity(title: String, start: Long, end: Long, subject: String, chapter: String,
                    kind: String, notes: String, onError: (String) -> Unit) = viewModelScope.launch {
        if (title.isBlank() || end <= start) {
            onError("Enter a title and an end time after the start time."); return@launch
        }
        repo.addActivity(ActivityRecord(UUID.randomUUID().toString(), title.trim(), start, end,
            end - start, subject, chapter, kind, notes.trim(), "Manual", 0, null))
    }

    fun addBacklog(title: String, subject: String, chapter: String, type: String,
                   priority: String, minutes: Int, deadline: Long?, notes: String,
                   onError: (String) -> Unit) = viewModelScope.launch {
        if (title.isBlank() || minutes < 0) { onError("Add a task title and a valid workload."); return@launch }
        val now = System.currentTimeMillis()
        repo.addBacklog(BacklogItem(UUID.randomUUID().toString(), title.trim(), subject,
            chapter, type, priority, 3, deadline, minutes, 0, "Not started", notes.trim(),
            null, now, now))
    }

    fun updateBacklog(item: BacklogItem) = viewModelScope.launch { repo.addBacklog(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteBacklog(id: String) = viewModelScope.launch { repo.deleteBacklog(id) }
    fun deleteActivity(id: String) = viewModelScope.launch { repo.deleteActivity(id) }
    fun updateChapter(chapter: Chapter) = viewModelScope.launch { repo.addChapter(chapter) }
    fun deleteChapter(id: String) = viewModelScope.launch { repo.deleteChapter(id) }

    fun startTimer(title: String, subject: String, chapter: String, backlogId: String?,
                   onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repo.startTimer(title, subject, chapter, backlogId) }
            .onFailure { onError(it.message ?: "Could not start timer.") }
    }
    fun pauseTimer() = viewModelScope.launch { repo.pauseTimer() }
    fun resumeTimer() = viewModelScope.launch { repo.resumeTimer() }
    fun stopTimer() = viewModelScope.launch { repo.stopTimer() }
    fun putSettings(value: AppSettings) = viewModelScope.launch { repo.saveSettings(value) }

    fun createTest(title: String, duration: Int, instructions: String, onCreated: (String) -> Unit) =
        viewModelScope.launch {
            if (title.isBlank() || duration <= 0) return@launch
            val id = UUID.randomUUID().toString()
            repo.addTest(PracticeTest(id, title.trim(), duration, System.currentTimeMillis(), instructions))
            onCreated(id)
        }

    fun addQuestion(question: TestQuestion) = viewModelScope.launch { repo.addQuestion(question) }
    fun startAttempt(testId: String, onStarted: (String) -> Unit) =
        viewModelScope.launch { onStarted(repo.createAttempt(testId)) }
    fun saveAttempt(attempt: TestAttempt) = viewModelScope.launch { repo.saveAttempt(attempt) }
    fun saveResponse(response: TestResponse) = viewModelScope.launch { repo.saveResponse(response) }
    fun questions(testId: String) = repo.questions(testId)
    fun attempt(id: String) = repo.attempt(id)
    fun responses(id: String) = repo.responses(id)
    fun attempts(testId: String) = repo.attempts(testId)
    suspend fun exportBackup() = repo.exportBackup()
    fun clearAllData(onDone: () -> Unit) = viewModelScope.launch { repo.clearAll(); onDone() }
    fun restoreBackup(json: String, onDone: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onDone(runCatching { repo.restoreBackup(json) })
    }
}

class JeeViewModelFactory(private val repository: StudyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(JeeViewModel::class.java))
        return JeeViewModel(repository) as T
    }
}
