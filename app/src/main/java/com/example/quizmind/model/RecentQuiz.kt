package com.example.quizmind.model

import androidx.annotation.DrawableRes

data class RecentQuiz(
    val id: String,
    val title: String,
    val questionInfo: String,
    val score: String,
    @param:DrawableRes val iconResId: Int
)
