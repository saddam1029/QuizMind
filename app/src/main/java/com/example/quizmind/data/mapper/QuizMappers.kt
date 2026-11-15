package com.example.quizmind.data.mapper

import com.example.quizmind.data.local.entity.HistoryEntity
import com.example.quizmind.data.local.entity.QuestionEntity
import com.example.quizmind.data.local.entity.QuizEntity
import com.example.quizmind.data.local.entity.QuizWithQuestions
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import java.util.UUID

fun QuizResult.toDownloadQuizEntity(): QuizEntity {
    return QuizEntity(
        id = quizId,
        title = topic,
        topic = topic,
        subject = subject,
        difficulty = difficulty,
        totalQuestions = totalQuestions,
        timerEnabled = timerEnabled,
        downloadedAt = System.currentTimeMillis()
    )
}

fun QuizResult.toDownloadQuestionEntities(): List<QuestionEntity> {
    return questions.mapIndexed { index, q ->
        QuestionEntity(
            id = q.id.ifBlank { UUID.randomUUID().toString() },
            quizId = quizId,
            questionIndex = index,
            question = q.question,
            optionA = q.options.getOrElse(0) { "" },
            optionB = q.options.getOrElse(1) { "" },
            optionC = q.options.getOrElse(2) { "" },
            optionD = q.options.getOrElse(3) { "" },
            correctAnswer = q.correctAnswer,
            explanation = q.explanation,
            hint = q.hint
        )
    }
}

fun QuizWithQuestions.toPlayableQuiz(): Quiz {
    val ordered = questions.sortedBy { it.questionIndex }
    return Quiz(
        id = quiz.id,
        topic = quiz.title.ifBlank { quiz.topic },
        subject = quiz.subject,
        difficulty = quiz.difficulty,
        questionCount = ordered.size,
        questions = ordered.map { it.toQuestion() },
        timerEnabled = quiz.timerEnabled
    )
}

fun QuestionEntity.toQuestion(): Question {
    return Question(
        id = id,
        question = question,
        options = listOf(optionA, optionB, optionC, optionD),
        correctAnswer = correctAnswer,
        explanation = explanation,
        hint = hint,
        selectedAnswer = null,
        isSkipped = false
    )
}

fun QuizResult.toHistoryEntity(synced: Boolean): HistoryEntity {
    return HistoryEntity(
        completionId = completionId,
        quizId = quizId,
        title = topic,
        topic = topic,
        subject = subject,
        difficulty = difficulty,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        wrongCount = wrongCount,
        skippedCount = skippedCount,
        correctMarks = correctMarks,
        negativeMarks = negativeMarks,
        finalScore = finalScore,
        maxScore = maxScore,
        accuracyPercentage = accuracyPercentage,
        timeTakenSeconds = timeTakenSeconds,
        completedAt = timestamp,
        source = source,
        firestoreSynced = synced
    )
}

fun HistoryEntity.toHistoryItem(isDownloaded: Boolean): HistoryItem {
    return HistoryItem(
        completionId = completionId,
        quizId = quizId,
        title = title,
        topic = topic,
        subject = subject,
        difficulty = difficulty,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        wrongCount = wrongCount,
        skippedCount = skippedCount,
        correctMarks = correctMarks,
        negativeMarks = negativeMarks,
        finalScore = finalScore,
        maxScore = maxScore,
        accuracyPercentage = accuracyPercentage,
        timeTakenSeconds = timeTakenSeconds,
        completedAt = completedAt,
        source = source,
        isDownloaded = isDownloaded,
        firestoreSynced = firestoreSynced
    )
}

fun HistoryItem.toQuizResultSummary(): QuizResult {
    return QuizResult(
        quizId = quizId,
        topic = title,
        subject = subject,
        difficulty = difficulty,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        wrongCount = wrongCount,
        skippedCount = skippedCount,
        correctMarks = correctMarks,
        negativeMarks = negativeMarks,
        finalScore = finalScore,
        maxScore = maxScore,
        accuracyPercentage = accuracyPercentage,
        timeTakenSeconds = timeTakenSeconds,
        questions = emptyList(),
        timestamp = completedAt,
        completionId = completionId,
        source = source
    )
}
