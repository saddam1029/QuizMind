package com.example.quizmind.data.remote

import android.util.Log
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
        private const val API_TIMEOUT_MS = 35_000L
        private const val MODEL_NAME = "gemini-2.5-flash"
    }

    suspend fun generateQuiz(
        topic: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ): Result<Quiz> = withContext(Dispatchers.IO) {
        val overallStartTime = System.currentTimeMillis()
        try {
            Log.d(
                TAG,
                "[Gemini] Generation started at: $overallStartTime | " +
                        "Topic='$topic', Subject='$subject', Count=$questionCount, Difficulty='$difficulty', Model='$MODEL_NAME'"
            )

            val ai = Firebase.ai(
                backend = GenerativeBackend.googleAI(),
                useLimitedUseAppCheckTokens = true
            )

            // Scaled output token budget to prevent unbounded decoding delays
            val tokenBudget = (questionCount * 220).coerceIn(600, 4000)

            val model = ai.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.2f
                    topP = 0.8f
                    maxOutputTokens = tokenBudget
                },
                systemInstruction = content {
                    text(
                        "You are an expert high-speed educational quiz generator. " +
                                "Generate high-quality multiple-choice questions matching the user requirements. " +
                                "Output strictly a valid JSON array of objects. Never include markdown code fences, " +
                                "conversational prose, or chain-of-thought commentary."
                    )
                }
            )

            val prompt = """
                Generate exactly $questionCount multiple-choice questions for:
                - Topic: "$topic"
                - Subject: "$subject"
                - Difficulty: "$difficulty"
                
                Format requirement: JSON array of objects with these exact keys:
                [
                  {
                    "question": "Question text here",
                    "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                    "correctAnswer": "Exact matching string from options list",
                    "explanation": "Clear 1-sentence educational explanation",
                    "hint": "Subtle 1-sentence hint"
                  }
                ]
                
                Rules:
                1. Provide exactly $questionCount questions.
                2. Each question must have exactly 4 distinct options.
                3. "correctAnswer" must match one of the four options identically.
                4. Keep question and option text concise and clear.
            """.trimIndent()

            val promptSentTime = System.currentTimeMillis()
            Log.d(TAG, "[Gemini] Prompt sent to Gemini at: $promptSentTime (preparation took ${promptSentTime - overallStartTime}ms)")

            // Coroutine timeout to guarantee the client never hangs indefinitely
            val response = withTimeout(API_TIMEOUT_MS) {
                model.generateContent(prompt)
            }

            val responseReceivedTime = System.currentTimeMillis()
            val apiDuration = responseReceivedTime - promptSentTime
            Log.d(TAG, "[Gemini] Gemini response received at: $responseReceivedTime (API roundtrip took ${apiDuration}ms)")

            val responseText = response.text?.trim()
            if (responseText.isNullOrEmpty()) {
                Log.e(TAG, "[Gemini] Gemini response text is null or empty.")
                return@withContext Result.failure(IllegalStateException("Empty response received from AI model."))
            }

            val parseStartTime = System.currentTimeMillis()
            val cleanJson = cleanJsonResponse(responseText)
            val parsedQuestions = parseAndValidateQuestions(cleanJson, questionCount)
            val parseEndTime = System.currentTimeMillis()

            Log.d(
                TAG,
                "[Gemini] Successfully parsed ${parsedQuestions.size} questions in ${parseEndTime - parseStartTime}ms. " +
                        "Total generation flow took ${parseEndTime - overallStartTime}ms."
            )

            val quiz = Quiz(
                id = UUID.randomUUID().toString(),
                topic = topic,
                subject = subject,
                difficulty = difficulty,
                questionCount = parsedQuestions.size,
                questions = parsedQuestions,
                timerEnabled = timerEnabled
            )

            Result.success(quiz)
        } catch (e: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - overallStartTime
            Log.e(TAG, "[Gemini] Quiz generation timed out after ${elapsed}ms.", e)
            Result.failure(IllegalStateException("Quiz generation timed out after ${elapsed / 1000}s. Please check your network and try again."))
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - overallStartTime
            Log.e(TAG, "[Gemini] Error generating quiz from AI after ${elapsed}ms: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun cleanJsonResponse(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json", ignoreCase = true)) {
            text = text.substring(7)
        } else if (text.startsWith("```")) {
            text = text.substring(3)
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3)
        }
        return text.trim()
    }

    private fun parseAndValidateQuestions(jsonString: String, expectedCount: Int): List<Question> {
        val questionsList = mutableListOf<Question>()

        val jsonArray = try {
            if (jsonString.startsWith("[")) {
                JSONArray(jsonString)
            } else if (jsonString.startsWith("{")) {
                val jsonObject = JSONObject(jsonString)
                val key = jsonObject.keys().asSequence().firstOrNull {
                    jsonObject.optJSONArray(it) != null
                }
                if (key != null) jsonObject.getJSONArray(key) else JSONArray().put(jsonObject)
            } else {
                throw IllegalArgumentException("Malformed JSON response format")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini] Failed to parse JSON array: ${e.message}", e)
            throw IllegalArgumentException("Failed to parse AI quiz response: ${e.localizedMessage}")
        }

        for (i in 0 until jsonArray.length()) {
            val qObj = jsonArray.optJSONObject(i) ?: continue

            val questionText = qObj.optString("question").trim()
            if (questionText.isEmpty()) continue

            val optionsArray = qObj.optJSONArray("options") ?: continue
            val options = mutableListOf<String>()
            for (j in 0 until optionsArray.length()) {
                val opt = optionsArray.optString(j).trim()
                if (opt.isNotEmpty()) {
                    options.add(opt)
                }
            }

            if (options.size < 4) continue
            val fourOptions = options.take(4)

            var correctAnswer = qObj.optString("correctAnswer").trim()
            if (correctAnswer.isEmpty() || !fourOptions.any { it.equals(correctAnswer, ignoreCase = true) }) {
                // Handle letter options (A, B, C, D)
                val letterIndex = when (correctAnswer.uppercase()) {
                    "A" -> 0
                    "B" -> 1
                    "C" -> 2
                    "D" -> 3
                    else -> -1
                }
                if (letterIndex in fourOptions.indices) {
                    correctAnswer = fourOptions[letterIndex]
                } else {
                    correctAnswer = fourOptions.firstOrNull { it.contains(correctAnswer, ignoreCase = true) }
                        ?: fourOptions[0]
                }
            } else {
                correctAnswer = fourOptions.first { it.equals(correctAnswer, ignoreCase = true) }
            }

            val explanation = qObj.optString("explanation").trim().ifEmpty {
                "The correct answer is $correctAnswer."
            }

            val hint = qObj.optString("hint").trim().ifEmpty {
                "Think about key concepts related to $questionText."
            }

            questionsList.add(
                Question(
                    id = UUID.randomUUID().toString(),
                    question = questionText,
                    options = fourOptions,
                    correctAnswer = correctAnswer,
                    explanation = explanation,
                    hint = hint
                )
            )
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
}
