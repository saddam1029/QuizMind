package com.example.quizmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.repository.QuizRepository
import com.example.quizmind.data.session.QuizSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Ready(val items: List<HistoryItem>, val offline: Boolean) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val quizSessionStore: QuizSessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    private var observeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            quizRepository.syncPendingHistory()

            if (quizRepository.isOnline()) {
                val remote = quizRepository.loadOnlineHistory(_query.value)
                remote.onSuccess { items ->
                    _uiState.value = HistoryUiState.Ready(items, offline = false)
                }.onFailure {
                    observeLocal(fallbackError = it.localizedMessage)
                }
            } else {
                observeLocal()
            }
        }
    }

    fun search(query: String) {
        _query.value = query.trim()
        refresh()
    }

    private fun observeLocal(fallbackError: String? = null) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            quizRepository.observeHistory(_query.value).collectLatest { items ->
                if (items.isEmpty() && fallbackError != null && quizRepository.isOnline().not()) {
                    _uiState.value = HistoryUiState.Ready(items, offline = true)
                } else {
                    _uiState.value = HistoryUiState.Ready(items, offline = !quizRepository.isOnline())
                }
            }
        }
    }

    fun rename(item: HistoryItem, newTitle: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = quizRepository.renameHistory(item, newTitle)
            onDone(result)
            refresh()
        }
    }

    fun delete(item: HistoryItem, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = quizRepository.deleteHistory(item)
            onDone(result)
            refresh()
        }
    }

    fun download(item: HistoryItem, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            if (quizRepository.isQuizDownloaded(item.quizId)) {
                onDone(Result.failure(IllegalStateException("Quiz already downloaded.")))
                return@launch
            }
            if (!quizRepository.isOnline()) {
                onDone(Result.failure(IllegalStateException("Internet is required to download this quiz.")))
                return@launch
            }
            val result = quizRepository.downloadHistoryItem(item.completionId)
            onDone(result)
            refresh()
        }
    }

    suspend fun prepareReplay(item: HistoryItem): Quiz? {
        val quiz = quizRepository.getPlayableDownloadedQuiz(item.quizId) ?: return null
        quizSessionStore.putQuiz(quiz, QuizResult.SOURCE_DOWNLOADED)
        return quiz
    }
}
