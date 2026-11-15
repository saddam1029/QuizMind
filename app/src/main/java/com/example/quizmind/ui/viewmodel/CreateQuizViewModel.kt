package com.example.quizmind.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.repository.QuizRepository
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.utils.TopicValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CreateQuizUiState {
    object Idle : CreateQuizUiState()
    object Loading : CreateQuizUiState()
    data class Success(val quiz: Quiz, val creditsUsed: Long) : CreateQuizUiState()
    data class Error(val message: String) : CreateQuizUiState()
}

@HiltViewModel
class CreateQuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val quizSessionStore: QuizSessionStore
) : ViewModel() {

    companion object {
        private const val TAG = "CreateQuizViewModel"
    }

    private val _uiState = MutableStateFlow<CreateQuizUiState>(CreateQuizUiState.Idle)
    val uiState: StateFlow<CreateQuizUiState> = _uiState.asStateFlow()

    private val _creditToast = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val creditToast: SharedFlow<Long> = _creditToast.asSharedFlow()

    fun generateTopicQuiz(
        rawTopic: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ) {
        generateQuizFromContent(
            title = rawTopic,
            sourceMaterial = "",
            subject = subject,
            questionCount = questionCount,
            difficulty = difficulty,
            timerEnabled = timerEnabled,
            isTopic = true
        )
    }

    fun generateQuizFromContent(
        title: String,
        sourceMaterial: String,
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

        val quizTitle = title.trim()
        val material = sourceMaterial.trim()
        if (isTopic) {
            val sanitized = TopicValidator.sanitizeTopic(quizTitle)
            if (!TopicValidator.isValid(sanitized)) {
                _uiState.value = CreateQuizUiState.Error("Please enter a valid topic (1-30 alphabetic characters).")
                return
            }
        } else if (material.isEmpty()) {
            _uiState.value = CreateQuizUiState.Error("Please provide a valid topic or study material.")
            return
        }

        if (questionCount <= 0) {
            _uiState.value = CreateQuizUiState.Error("Please select a valid question count.")
            return
        }

        val resolvedTitle = if (isTopic) TopicValidator.sanitizeTopic(quizTitle) else quizTitle
        Log.d(TAG, "Requesting quiz generation: count=$questionCount, subject='$subject', difficulty='$difficulty'")
        _uiState.value = CreateQuizUiState.Loading

        viewModelScope.launch {
            val result = quizRepository.generateQuiz(
                title = resolvedTitle,
                sourceMaterial = if (isTopic) "" else material,
                subject = subject,
                questionCount = questionCount,
                difficulty = difficulty,
                timerEnabled = timerEnabled
            )

            result.fold(
                onSuccess = { (generated, deduction) ->
                    Log.d(
                        TAG,
                        "Quiz generated with ${generated.quiz.questions.size} questions. " +
                            "Credits used=${deduction.deducted}"
                    )
                    quizSessionStore.putQuiz(generated.quiz, QuizResult.SOURCE_ONLINE)
                    if (deduction.deducted > 0L) {
                        _creditToast.emit(deduction.deducted)
                    }
                    _uiState.value = CreateQuizUiState.Success(generated.quiz, deduction.deducted)
                },
                onFailure = { throwable ->
                    Log.e(TAG, "Quiz generation failed: ${throwable.message}", throwable)
                    val userFriendlyMsg = when {
                        throwable is kotlinx.coroutines.TimeoutCancellationException ||
                            throwable.message?.contains("timed out", ignoreCase = true) == true ->
                            "Quiz generation timed out. Please check your internet connection and try again."
                        throwable.message?.contains("Insufficient credits", ignoreCase = true) == true ->
                            throwable.localizedMessage ?: "Insufficient credits."
                        throwable.message?.contains("quota", ignoreCase = true) == true ||
                            throwable.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ->
                            "AI service quota exceeded. Please wait a moment and try again."
                        throwable.message?.contains("network", ignoreCase = true) == true ||
                            throwable.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                            throwable.message?.contains("Failed to connect", ignoreCase = true) == true ->
                            "Network connection error. Please verify your internet connection and retry."
                        throwable.message?.contains("App Check", ignoreCase = true) == true ->
                            "App Check verification failed. Please try again."
                        throwable.message?.contains("not found", ignoreCase = true) == true ||
                            throwable.message?.contains("no longer available", ignoreCase = true) == true ||
                            throwable.message?.contains("is not supported", ignoreCase = true) == true ->
                            "The quiz model is unavailable right now. Please try again in a moment."
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

    fun warmUpAi() {
        viewModelScope.launch {
            runCatching { quizRepository.warmUpAi() }
        }
    }
}
