package com.example.quizmind.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.repository.QuizRepository
import com.example.quizmind.utils.TopicValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CreateQuizUiState {
    object Idle : CreateQuizUiState()
    object Loading : CreateQuizUiState()
    data class Success(val quiz: Quiz) : CreateQuizUiState()
    data class Error(val message: String) : CreateQuizUiState()
}

@HiltViewModel
class CreateQuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CreateQuizViewModel"
    }

    private val _uiState = MutableStateFlow<CreateQuizUiState>(CreateQuizUiState.Idle)
    val uiState: StateFlow<CreateQuizUiState> = _uiState.asStateFlow()

    fun generateTopicQuiz(
        rawTopic: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ) {
        generateQuizFromContent(rawTopic, subject, questionCount, difficulty, timerEnabled, isTopic = true)
    }

    fun generateQuizFromContent(
        rawContent: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean,
        isTopic: Boolean
    ) {
        if (_uiState.value is CreateQuizUiState.Loading) {
            Log.w(TAG, "generateQuizFromContent ignored: Generation already in progress.")
            return
        }

        val content = rawContent.trim()
        if (content.isEmpty()) {
            _uiState.value = CreateQuizUiState.Error("Please provide a valid topic or study material.")
            return
        }

        if (isTopic) {
            val sanitized = TopicValidator.sanitizeTopic(content)
            if (!TopicValidator.isValid(sanitized)) {
                _uiState.value = CreateQuizUiState.Error("Please enter a valid topic (1-30 alphabetic characters).")
                return
            }
        }

        if (questionCount <= 0) {
            _uiState.value = CreateQuizUiState.Error("Please select a valid question count.")
            return
        }

        Log.d(TAG, "Requesting quiz generation: count=$questionCount, subject='$subject', difficulty='$difficulty'")
        _uiState.value = CreateQuizUiState.Loading

        viewModelScope.launch {
            val result = quizRepository.generateQuiz(
                topic = content,
                subject = subject,
                questionCount = questionCount,
                difficulty = difficulty,
                timerEnabled = timerEnabled
            )

            result.fold(
                onSuccess = { quiz ->
                    Log.d(TAG, "Quiz successfully generated with ${quiz.questions.size} questions.")
                    _uiState.value = CreateQuizUiState.Success(quiz)
                },
                onFailure = { throwable ->
                    Log.e(TAG, "Quiz generation failed: ${throwable.message}", throwable)
                    val userFriendlyMsg = when {
                        throwable is kotlinx.coroutines.TimeoutCancellationException ||
                        throwable.message?.contains("timed out", ignoreCase = true) == true ->
                            "Quiz generation timed out. Please check your internet connection and try again."
                        throwable.message?.contains("quota", ignoreCase = true) == true ||
                        throwable.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ->
                            "AI service quota exceeded. Please wait a moment and try again."
                        throwable.message?.contains("network", ignoreCase = true) == true ||
                        throwable.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                        throwable.message?.contains("Failed to connect", ignoreCase = true) == true ->
                            "Network connection error. Please verify your internet connection and retry."
                        throwable.message?.contains("App Check", ignoreCase = true) == true ->
                            "App Check verification failed. Please try again."
                        else ->
                            throwable.localizedMessage ?: "Failed to generate quiz. Please retry."
                    }
                    _uiState.value = CreateQuizUiState.Error(userFriendlyMsg)
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = CreateQuizUiState.Idle
    }
}
