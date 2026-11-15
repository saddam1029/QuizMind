package com.example.quizmind.data.credits

/**
 * QuizMind has no separate pricing config in the project.
 *
 * Documented policy used by the app:
 * - New accounts start with [STARTING_CREDITS] (matches the previous Home placeholder).
 * - Successful Gemini generation deducts [usageTokenCount] QuizMind credits
 *   (1 credit ≈ 1 Gemini totalTokenCount from usageMetadata).
 * - Failed or cancelled generation does not deduct credits.
 * - If usage metadata is missing, [fallbackCost] is used.
 */
object CreditPolicy {
    const val STARTING_CREDITS = 10_000L

    fun fallbackCost(questionCount: Int): Long {
        // flash-lite uses fewer tokens; keep a light local estimate for the pre-check.
        return (questionCount.coerceAtLeast(1) * 45L)
    }

    fun resolveCost(usageTokenCount: Int?, questionCount: Int): Long {
        val fromUsage = usageTokenCount?.toLong() ?: 0L
        return if (fromUsage > 0L) fromUsage else fallbackCost(questionCount)
    }
}
