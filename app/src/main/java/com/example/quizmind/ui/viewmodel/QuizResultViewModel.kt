package com.example.quizmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.repository.QuizRepository
import com.example.quizmind.data.session.QuizSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DownloadState {
    object Idle : DownloadState()
    object Downloading : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
    object AlreadyDownloaded : DownloadState()
}

@HiltViewModel
class QuizResultViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val quizSessionStore: QuizSessionStore
) : ViewModel() {

    fun getResult(quizId: String): QuizResult? = quizSessionStore.getResult(quizId)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    fun checkIsDownloaded(quizId: String) {
        viewModelScope.launch {
            val downloaded = quizRepository.isQuizDownloaded(quizId)
            _isDownloaded.value = downloaded
        }
    }

    fun downloadQuiz(result: QuizResult) {
        if (_isDownloaded.value) {
            _downloadState.value = DownloadState.AlreadyDownloaded
            return
        }

        _downloadState.value = DownloadState.Downloading
        viewModelScope.launch {
            val downloadResult = quizRepository.downloadQuizToRoom(result)
            downloadResult.fold(
                onSuccess = {
                    _isDownloaded.value = true
                    _downloadState.value = DownloadState.Success
                },
                onFailure = { e ->
                    if (e.message?.contains("already", ignoreCase = true) == true) {
                        _isDownloaded.value = true
                        _downloadState.value = DownloadState.AlreadyDownloaded
                    } else {
                        _downloadState.value = DownloadState.Error(
                            e.localizedMessage ?: "Failed to save quiz to offline storage."
                        )
                    }
                }
            )
        }
    }
}
