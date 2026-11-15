package com.example.quizmind.data.remote

import android.util.Log
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiQuizService @Inject constructor() {

    companion object {
        private const val TAG = "GeminiQuizService"
        private const val API_TIMEOUT_MS = 60_000L
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val MAX_SOURCE_CHARS = 6_000
        private const val MAX_ATTEMPTS = 2
    }

    suspend fun generateQuiz(
        title: String,
        sourceMaterial: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ): Result<Quiz> = withContext(Dispatchers.IO) {
        val excerpt = compactExcerpt(sourceMaterial, MAX_SOURCE_CHARS)
        var lastError: Throwable? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            val overallStartTime = System.currentTimeMillis()
            try {
                Log.d(
                    TAG,
                    "[Gemini] Attempt ${attempt + 1}/$MAX_ATTEMPTS started. " +
                        "Title='$title', Subject='$subject', Count=$questionCount, " +
                        "Difficulty='$difficulty', Model='$MODEL_NAME', SourceChars=${excerpt.length}"
                )

                val quiz = requestQuiz(
                    title = title,
                    sourceExcerpt = excerpt,
                    subject = subject,
                    questionCount = questionCount,
                    difficulty = difficulty,
                    timerEnabled = timerEnabled,
                    startedAt = overallStartTime
                )
                return@withContext Result.success(quiz)
            } catch (e: TimeoutCancellationException) {
                val elapsed = System.currentTimeMillis() - overallStartTime
                Log.e(TAG, "[Gemini] Quiz generation timed out after ${elapsed}ms.", e)
                return@withContext Result.failure(
                    IllegalStateException(
                        "Quiz generation timed out after ${elapsed / 1000}s. Please check your network and try again."
                    )
                )
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - overallStartTime
                Log.e(TAG, "[Gemini] Attempt ${attempt + 1} failed after ${elapsed}ms: ${e.message}", e)
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1 && isRetryable(e)) {
                    delay(1_200L)
                } else {
                    return@withContext Result.failure(e)
                }
            }
        }

        Result.failure(lastError ?: IllegalStateException("Failed to generate quiz. Please retry."))
    }

    private suspend fun requestQuiz(
        title: String,
        sourceExcerpt: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean,
        startedAt: Long
    ): Quiz {
        val ai = Firebase.ai(
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true
        )

        // Leave room for a short thinking pass and the JSON answers.
        val tokenBudget = (questionCount * 360).coerceIn(1_600, 8_192)

        val model = ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                maxOutputTokens = tokenBudget
                thinkingConfig = thinkingConfig {
                    // Gemini 3.8 Flash rejects MINIMAL. LOW is the fastest supported level.
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content {
                text(
                    "You write short multiple-choice quizzes. " +
                        "Return only a JSON array. No markdown, no commentary."
                )
            }
        )

        val prompt = buildPrompt(title, sourceExcerpt, subject, questionCount, difficulty)
        val promptSentTime = System.currentTimeMillis()
        Log.d(TAG, "[Gemini] Prompt sent. Preparation took ${promptSentTime - startedAt}ms")

        val response = withTimeout(API_TIMEOUT_MS) {
            model.generateContent(prompt)
        }

        val responseReceivedTime = System.currentTimeMillis()
        Log.d(
            TAG,
            "[Gemini] Response received. API roundtrip took ${responseReceivedTime - promptSentTime}ms"
        )

        val responseText = response.text?.trim()
        if (responseText.isNullOrEmpty()) {
            throw IllegalStateException("Empty response received from AI model.")
        }

        val parsedQuestions = parseAndValidateQuestions(cleanJsonResponse(responseText), questionCount)
        Log.d(
            TAG,
            "[Gemini] Parsed ${parsedQuestions.size} questions. " +
                "Total flow took ${System.currentTimeMillis() - startedAt}ms."
        )

        return Quiz(
            id = UUID.randomUUID().toString(),
            topic = title,
            subject = subject,
            difficulty = difficulty,
            questionCount = parsedQuestions.size,
            questions = parsedQuestions,
            timerEnabled = timerEnabled
        )
    }

    private fun buildPrompt(
        title: String,
        sourceExcerpt: String,
        subject: String,
        questionCount: Int,
        difficulty: String
    ): String {
        val materialBlock = if (sourceExcerpt.isBlank()) {
            "Create original questions about the topic."
        } else {
            "Base every question only on this study material:\n\"\"\"\n$sourceExcerpt\n\"\"\""
        }

        return """
            Generate exactly $questionCount multiple-choice questions.
            Title: "$title"
            Subject: "$subject"
            Difficulty: "$difficulty"
            $materialBlock

            Return a JSON array with exactly these keys:
            [{"question":"","options":["","","",""],"correctAnswer":"","explanation":"","hint":""}]

            Rules:
            1. Exactly $questionCount questions, each with exactly 4 short options.
            2. correctAnswer must be copied exactly from that question's options.
            3. Keep each question, option, explanation, and hint to one short sentence.
        """.trimIndent()
    }

    private fun isRetryable(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        if (
            message.contains("not found", ignoreCase = true) ||
            message.contains("is not supported", ignoreCase = true) ||
            message.contains("App Check", ignoreCase = true) ||
            message.contains("INVALID_ARGUMENT", ignoreCase = true)
        ) {
            return false
        }
        return error is IllegalStateException ||
            error is IllegalArgumentException ||
            message.contains("unavailable", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("429") ||
            message.contains("500") ||
            message.contains("503") ||
            message.contains("quota", ignoreCase = true) ||
            message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("Failed to connect", ignoreCase = true)
    }

    private fun compactExcerpt(raw: String, maxChars: Int): String {
        if (raw.isBlank()) return ""
        val builder = StringBuilder(minOf(raw.length, maxChars))
        var pendingSpace = false
        for (char in raw) {
            if (char.isWhitespace()) {
                pendingSpace = builder.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                if (builder.length + 1 >= maxChars) break
                builder.append(' ')
                pendingSpace = false
            }
            if (builder.length >= maxChars) break
            builder.append(char)
        }
        return builder.toString()
    }

    private fun cleanJsonResponse(rawText: String): String {
        var text = rawText.trim()
        val fenceStart = text.indexOf("```")
        if (fenceStart >= 0) {
            text = text.substring(fenceStart + 3)
            if (text.startsWith("json", ignoreCase = true)) {
                text = text.substring(4)
            }
            val fenceEnd = text.lastIndexOf("```")
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd)
            }
        }
        return text.trim()
    }

    private fun parseAndValidateQuestions(jsonString: String, expectedCount: Int): List<Question> {
        val jsonArray = try {
            decodeQuestionArray(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini] Failed to parse JSON array: ${e.message}", e)
            throw IllegalArgumentException("Failed to parse AI quiz response: ${e.localizedMessage}")
        }

        val questionsList = mutableListOf<Question>()
        for (i in 0 until jsonArray.length()) {
            val question = jsonArray.optJSONObject(i)?.let { toQuestion(it) } ?: continue
            questionsList.add(question)
        }

        if (questionsList.isEmpty()) {
            Log.w(TAG, "[Gemini] Parsed 0 valid questions from JSON response.")
            throw IllegalStateException("AI generated 0 valid questions. Please try again.")
        }

        return if (questionsList.size > expectedCount) {
            questionsList.take(expectedCount)
        } else {
            questionsList
        }
    }

    private fun decodeQuestionArray(jsonString: String): JSONArray {
        return try {
            readArray(jsonString)
        } catch (firstError: Exception) {
            val repaired = repairTruncatedArray(jsonString)
            try {
                readArray(repaired)
            } catch (_: Exception) {
                throw firstError
            }
        }
    }

    private fun readArray(jsonString: String): JSONArray {
        val text = jsonString.trim()
        return when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> {
                val jsonObject = JSONObject(text)
                val key = jsonObject.keys().asSequence().firstOrNull {
                    jsonObject.optJSONArray(it) != null
                }
                if (key != null) jsonObject.getJSONArray(key) else JSONArray().put(jsonObject)
            }
            else -> throw IllegalArgumentException("Malformed JSON response format")
        }
    }

    private fun repairTruncatedArray(jsonString: String): String {
        val start = jsonString.indexOf('[')
        if (start < 0) throw IllegalArgumentException("Malformed JSON response format")
        val body = jsonString.substring(start)
        val lastObject = body.lastIndexOf('}')
        if (lastObject < 0) throw IllegalArgumentException("Malformed JSON response format")
        return body.substring(0, lastObject + 1).trimEnd().trimEnd(',') + "]"
    }

    private fun toQuestion(qObj: JSONObject): Question? {
        val questionText = qObj.optString("question").trim()
        if (questionText.isEmpty()) return null

        val optionsArray = qObj.optJSONArray("options") ?: return null
        val options = mutableListOf<String>()
        for (j in 0 until optionsArray.length()) {
            val option = optionsArray.optString(j).trim()
            if (option.isNotEmpty()) {
                options.add(option)
            }
        }
        if (options.size < 4) return null
        val fourOptions = options.take(4)

        var correctAnswer = qObj.optString("correctAnswer").trim()
        if (correctAnswer.isEmpty() || fourOptions.none { it.equals(correctAnswer, ignoreCase = true) }) {
            val letterIndex = when (correctAnswer.uppercase()) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> -1
            }
            correctAnswer = when {
                letterIndex in fourOptions.indices -> fourOptions[letterIndex]
                else -> fourOptions.firstOrNull { option ->
                    correctAnswer.isNotEmpty() && option.contains(correctAnswer, ignoreCase = true)
                } ?: return null
            }
        } else {
            correctAnswer = fourOptions.first { it.equals(correctAnswer, ignoreCase = true) }
        }

        val explanation = qObj.optString("explanation").trim().ifEmpty {
            "The correct answer is $correctAnswer."
        }
        val hint = qObj.optString("hint").trim().ifEmpty {
            "Think about the main idea in this question."
        }

        return Question(
            id = UUID.randomUUID().toString(),
            question = questionText,
            options = fourOptions,
            correctAnswer = correctAnswer,
            explanation = explanation,
            hint = hint
        )
    }
}
