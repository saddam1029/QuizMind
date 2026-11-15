package com.example.quizmind.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.repository.QuizRepository
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.utils.QuizScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizResultLaunch(
    val quizId: String,
    val sequence: Int
)

@HiltViewModel
class ActiveQuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val quizSessionStore: QuizSessionStore
) : ViewModel() {

    companion object {
        private const val TAG = "ActiveQuizViewModel"
    }

    private var quiz: Quiz? = null
    private val questions = mutableListOf<Question>()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _selectedAnswer = MutableStateFlow<String?>(null)
    val selectedAnswer: StateFlow<String?> = _selectedAnswer.asStateFlow()

    private val _elapsedTimeSeconds = MutableStateFlow(0L)
    val elapsedTimeSeconds: StateFlow<Long> = _elapsedTimeSeconds.asStateFlow()

    private val _resultLaunch = MutableStateFlow<QuizResultLaunch?>(null)
    val resultLaunch: StateFlow<QuizResultLaunch?> = _resultLaunch.asStateFlow()

    private var timerJob: Job? = null
    private var isTimerRunning = false
    private var finishedResult: QuizResult? = null
    private var launchSequence = 0

    fun initializeQuiz(quizId: String): Boolean {
        if (quiz != null) {
            Log.d(TAG, "initializeQuiz: already initialized with quizId=${quiz?.id}")
            return true
        }
        val initialQuiz = quizSessionStore.getQuiz(quizId)
        if (initialQuiz == null || initialQuiz.questions.isEmpty()) {
            Log.e(TAG, "initializeQuiz: quiz '$quizId' was not found in the session.")
            return false
        }
        Log.d(TAG, "initializeQuiz: starting quiz '${initialQuiz.topic}' with ${initialQuiz.questions.size} questions.")
        quiz = initialQuiz
        questions.clear()
        questions.addAll(initialQuiz.questions)
        _currentQuestionIndex.value = 0
        _selectedAnswer.value = questions.firstOrNull()?.selectedAnswer
        startTimer()
        return true
    }

    private fun startTimer() {
        if (isTimerRunning) return
        isTimerRunning = true
        Log.d(TAG, "Quiz timer started at 00:00")
        timerJob = viewModelScope.launch {
            while (isTimerRunning) {
                delay(1000)
                _elapsedTimeSeconds.value += 1
            }
        }
    }

    fun stopTimer() {
        if (isTimerRunning) {
            Log.d(TAG, "Quiz timer stopped at ${_elapsedTimeSeconds.value} seconds.")
        }
        isTimerRunning = false
        timerJob?.cancel()
        timerJob = null
    }

    fun selectOption(option: String) {
        val index = _currentQuestionIndex.value
        if (index in questions.indices) {
            Log.d(TAG, "Question #$index: Selected option '$option'")
            questions[index].selectedAnswer = option
            questions[index].isSkipped = false
            _selectedAnswer.value = option
        }
    }

    fun skipCurrentQuestion() {
        val index = _currentQuestionIndex.value
        if (index in questions.indices) {
            Log.d(TAG, "Question #$index: Skipped by user")
            questions[index].selectedAnswer = null
            questions[index].isSkipped = true
            _selectedAnswer.value = null
            moveToNextOrFinish()
        }
    }

    fun nextQuestion(): Boolean {
        val index = _currentQuestionIndex.value
        if (index in questions.indices) {
            val currentQ = questions[index]
            if (currentQ.selectedAnswer.isNullOrBlank() && !currentQ.isSkipped) {
                Log.d(TAG, "Cannot proceed: Question #$index has no answer selected and is not skipped.")
                return false
            }
            moveToNextOrFinish()
            return true
        }
        return false
    }

    fun isLastQuestion(): Boolean {
        val index = _currentQuestionIndex.value
        return index >= questions.size - 1
    }

    fun previousQuestion(): Boolean {
        val index = _currentQuestionIndex.value
        if (index > 0) {
            _currentQuestionIndex.value = index - 1
            _selectedAnswer.value = questions[index - 1].selectedAnswer
            return true
        }
        return false
    }

    private fun moveToNextOrFinish() {
        val index = _currentQuestionIndex.value
        if (index < questions.size - 1) {
            val nextIndex = index + 1
            Log.d(TAG, "Moving from question #$index to #$nextIndex")
            _currentQuestionIndex.value = nextIndex
            _selectedAnswer.value = questions[nextIndex].selectedAnswer
        } else {
            Log.d(TAG, "At last question (#$index). Triggering finishQuiz().")
            finishQuiz()
        }
    }

    fun finishQuiz() {
        val existing = finishedResult
        if (existing != null) {
            Log.d(TAG, "finishQuiz: result already ready. Opening results again for quizId=${existing.quizId}")
            publishResult(existing.quizId)
            return
        }

        stopTimer()

        val currentQuiz = quiz ?: run {
            Log.e(TAG, "finishQuiz: quiz is null!")
            return
        }
        val finalTimeSeconds = _elapsedTimeSeconds.value
        currentQuiz.timeTakenSeconds = finalTimeSeconds

        Log.d(TAG, "Calculating final score for ${questions.size} questions...")
        val score = QuizScoreCalculator.calculateScore(questions)
        Log.d(
            TAG,
            "Score calculation complete: Correct=${score.correctCount} (+${score.correctMarks}), " +
                    "Wrong=${score.wrongCount} (${score.negativeMarks}), " +
                    "Skipped=${score.skippedCount}, Final=${score.finalScore}/${score.maxScore}, " +
                    "Accuracy=${score.accuracyPercentage}%"
        )

        val result = QuizResult(
            quizId = currentQuiz.id,
            topic = currentQuiz.topic,
            subject = currentQuiz.subject,
            difficulty = currentQuiz.difficulty,
            totalQuestions = score.totalQuestions,
            correctCount = score.correctCount,
            wrongCount = score.wrongCount,
            skippedCount = score.skippedCount,
            correctMarks = score.correctMarks,
            negativeMarks = score.negativeMarks,
            finalScore = score.finalScore,
            maxScore = score.maxScore,
            accuracyPercentage = score.accuracyPercentage,
            timeTakenSeconds = finalTimeSeconds,
            questions = questions.toList()
        )
        finishedResult = result
        quizSessionStore.putResult(result)
        publishResult(result.quizId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Saving quiz result to Firestore in background...")
                val saveRes = quizRepository.saveQuizResultToFirestore(result)
                if (saveRes.isSuccess) {
                    Log.d(TAG, "Quiz result successfully saved to Firestore.")
                } else {
                    Log.w(TAG, "Firestore save returned error (non-fatal): ${saveRes.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background Firestore save exception (non-fatal): ${e.message}")
            }
        }
    }

    private fun publishResult(quizId: String) {
        launchSequence += 1
        Log.d(TAG, "Publishing results navigation #$launchSequence for quizId=$quizId")
        _resultLaunch.value = QuizResultLaunch(quizId, launchSequence)
    }

    fun getCurrentQuestion(): Question? {
        val index = _currentQuestionIndex.value
        return questions.getOrNull(index)
    }

    fun getTotalQuestions(): Int = questions.size

    fun getQuizTitle(): String = quiz?.topic ?: "Quiz"

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
