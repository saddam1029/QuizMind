package com.example.quizmind.data.remote

import android.util.Log
import com.example.quizmind.data.model.GeneratedQuiz
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiQuizService @Inject constructor() {

    companion object {
        private const val TAG = "GeminiQuizService"
        private const val API_TIMEOUT_MS = 25_000L
        // Fastest supported text model for JSON quiz extraction.
        private const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val MAX_SOURCE_CHARS = 2_500
    }

    private val modelMutex = Mutex()
    private val modelCache = ConcurrentHashMap<Int, GenerativeModel>()

    /**
     * Warm the Firebase AI client so the first Generate Quiz tap is faster.
     */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        runCatching { getModel(questionCount = 5) }
            .onFailure { Log.w(TAG, "AI warm-up skipped: ${it.message}") }
    }

    suspend fun generateQuiz(
        title: String,
        sourceMaterial: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ): Result<GeneratedQuiz> = withContext(Dispatchers.IO) {
        val overallStartTime = System.currentTimeMillis()
        try {
            val excerpt = compactExcerpt(sourceMaterial, MAX_SOURCE_CHARS)
            Log.d(
                TAG,
                "[Gemini] Fast generation start. Title='$title', Count=$questionCount, " +
                    "Model='$MODEL_NAME', SourceChars=${excerpt.length}"
            )

            val model = getModel(questionCount)
            val prompt = buildPrompt(title, excerpt, subject, questionCount, difficulty)
            val promptSentTime = System.currentTimeMillis()

            val response = withTimeout(API_TIMEOUT_MS) {
                model.generateContent(prompt)
            }

            Log.d(
                TAG,
                "[Gemini] Response in ${System.currentTimeMillis() - promptSentTime}ms"
            )

            val responseText = response.text?.trim()
            if (responseText.isNullOrEmpty()) {
                throw IllegalStateException("Empty response received from AI model.")
            }

            val usageTokens = response.usageMetadata?.totalTokenCount ?: 0
            val parsedQuestions = parseAndValidateQuestions(
                cleanJsonResponse(responseText),
                questionCount
            )

            Log.d(
                TAG,
                "[Gemini] Parsed ${parsedQuestions.size} questions in " +
                    "${System.currentTimeMillis() - overallStartTime}ms (tokens=$usageTokens)"
            )

            Result.success(
                GeneratedQuiz(
                    quiz = Quiz(
                        id = UUID.randomUUID().toString(),
                        topic = title,
                        subject = subject,
                        difficulty = difficulty,
                        questionCount = parsedQuestions.size,
                        questions = parsedQuestions,
                        timerEnabled = timerEnabled
                    ),
                    usageTokenCount = usageTokens
                )
            )
        } catch (e: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - overallStartTime
            Log.e(TAG, "[Gemini] Timed out after ${elapsed}ms", e)
            Result.failure(
                IllegalStateException(
                    "Quiz generation timed out after ${elapsed / 1000}s. Please try again."
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini] Failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun getModel(questionCount: Int): GenerativeModel {
        // Bucket token budgets so we reuse a few model instances.
        val bucket = when {
            questionCount <= 5 -> 5
            questionCount <= 10 -> 10
            questionCount <= 15 -> 15
            else -> 20
        }
        modelCache[bucket]?.let { return it }
        return modelMutex.withLock {
            modelCache[bucket] ?: createModel(bucket).also { modelCache[bucket] = it }
        }
    }

    private fun createModel(questionCount: Int): GenerativeModel {
        val ai = Firebase.ai(
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true
        )
        // Compact MCQ JSON needs far fewer tokens than the previous budget.
        val tokenBudget = (questionCount * 140).coerceIn(700, 3_200)
        return ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                maxOutputTokens = tokenBudget
                thinkingConfig = thinkingConfig {
                    // flash-lite supports MINIMAL — lowest latency.
                    thinkingLevel = ThinkingLevel.MINIMAL
                }
            },
            systemInstruction = content {
                text("Return only a JSON array of MCQ objects. No markdown.")
            }
        )
    }

    private fun buildPrompt(
        title: String,
        sourceExcerpt: String,
        subject: String,
        questionCount: Int,
        difficulty: String
    ): String {
        val material = if (sourceExcerpt.isBlank()) {
            "Topic only."
        } else {
            "Material:\n$sourceExcerpt"
        }
        return """
            Make exactly $questionCount MCQs.
            Title:$title | Subject:$subject | Difficulty:$difficulty
            $material
            JSON only:
            [{"q":"...","o":["A","B","C","D"],"a":"A","e":"...","h":"..."}]
            Rules: 4 short options; a must match one option exactly; e/h one short clause each.
        """.trimIndent()
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
            throw IllegalArgumentException("Failed to parse AI quiz response: ${e.localizedMessage}")
        }

        val questionsList = mutableListOf<Question>()
        for (i in 0 until jsonArray.length()) {
            val question = jsonArray.optJSONObject(i)?.let { toQuestion(it) } ?: continue
            questionsList.add(question)
        }

        if (questionsList.isEmpty()) {
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
            try {
                readArray(repairTruncatedArray(jsonString))
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
        // Support compact keys (q/o/a/e/h) and full keys for compatibility.
        val questionText = qObj.optString("q").ifBlank { qObj.optString("question") }.trim()
        if (questionText.isEmpty()) return null

        val optionsArray = qObj.optJSONArray("o") ?: qObj.optJSONArray("options") ?: return null
        val options = mutableListOf<String>()
        for (j in 0 until optionsArray.length()) {
            val option = optionsArray.optString(j).trim()
            if (option.isNotEmpty()) options.add(option)
        }
        if (options.size < 4) return null
        val fourOptions = options.take(4)

        var correctAnswer = qObj.optString("a").ifBlank { qObj.optString("correctAnswer") }.trim()
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

        val explanation = qObj.optString("e").ifBlank { qObj.optString("explanation") }.trim()
            .ifEmpty { "The correct answer is $correctAnswer." }
        val hint = qObj.optString("h").ifBlank { qObj.optString("hint") }.trim()
            .ifEmpty { "Think about the main idea in this question." }

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
