package com.example.quizmind.data.mapper

import com.example.quizmind.data.local.entity.HistoryEntity
import com.example.quizmind.data.local.entity.QuestionEntity
import com.example.quizmind.data.local.entity.QuizEntity
import com.example.quizmind.data.local.entity.QuizWithQuestions
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.model.QuizSourceType
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
        sourceType = QuizSourceType.normalize(sourceType),
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
        timerEnabled = quiz.timerEnabled,
        sourceType = QuizSourceType.normalize(quiz.sourceType)
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

fun QuizResult.toHistoryEntity(synced: Boolean, persistQuestions: Boolean = false): HistoryEntity {
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
        sourceType = QuizSourceType.normalize(sourceType),
        firestoreSynced = synced,
        questionsJson = if (persistQuestions && questions.isNotEmpty()) {
            questions.toQuestionsJson()
        } else {
            null
        }
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
        sourceType = QuizSourceType.normalize(sourceType),
        isDownloaded = isDownloaded,
        firestoreSynced = firestoreSynced,
        isCompleted = true
    )
}

fun QuizEntity.toUnattemptedHistoryItem(): HistoryItem {
    return HistoryItem(
        completionId = HistoryItem.downloadPlaceholderId(id),
        quizId = id,
        title = title.ifBlank { topic },
        topic = topic,
        subject = subject,
        difficulty = difficulty,
        totalQuestions = totalQuestions,
        correctCount = 0,
        wrongCount = 0,
        skippedCount = 0,
        correctMarks = 0,
        negativeMarks = 0,
        finalScore = 0,
        maxScore = 0,
        accuracyPercentage = 0,
        timeTakenSeconds = 0L,
        completedAt = downloadedAt,
        source = QuizResult.SOURCE_DOWNLOADED,
        sourceType = QuizSourceType.normalize(sourceType),
        isDownloaded = true,
        firestoreSynced = true,
        isCompleted = false
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
        source = source,
        sourceType = QuizSourceType.normalize(sourceType)
    )
}

fun HistoryEntity.toQuizResult(questions: List<Question>): QuizResult {
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
        questions = questions,
        timestamp = completedAt,
        completionId = completionId,
        source = source,
        sourceType = QuizSourceType.normalize(sourceType)
    )
}
