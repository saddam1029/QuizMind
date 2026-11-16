package com.example.quizmind.data.mapper

import com.example.quizmind.data.model.Question
import org.json.JSONArray
import org.json.JSONObject

fun List<Question>.toQuestionsJson(): String {
    val array = JSONArray()
    forEach { q ->
        val obj = JSONObject()
        obj.put("id", q.id)
        obj.put("question", q.question)
        obj.put("options", JSONArray(q.options))
        obj.put("correctAnswer", q.correctAnswer)
        obj.put("explanation", q.explanation)
        obj.put("hint", q.hint)
        obj.put("selectedAnswer", q.selectedAnswer)
        obj.put("isSkipped", q.isSkipped)
        array.put(obj)
    }
    return array.toString()
}

fun String?.toQuestionList(): List<Question> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(this)
        buildList {
            for (i in 0 until array.length()) {
                val q = array.getJSONObject(i)
                val optionsJson = q.optJSONArray("options")
                val options = buildList {
                    if (optionsJson != null) {
                        for (j in 0 until optionsJson.length()) {
                            add(optionsJson.optString(j))
                        }
                    }
                }
                add(
                    Question(
                        id = q.optString("id"),
                        question = q.optString("question"),
                        options = options,
                        correctAnswer = q.optString("correctAnswer"),
                        explanation = q.optString("explanation"),
                        hint = q.optString("hint"),
                        selectedAnswer = q.optString("selectedAnswer").ifBlank { null },
                        isSkipped = q.optBoolean("isSkipped", false)
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
