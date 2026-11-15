package com.example.quizmind.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class CircularScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score: Int = 0
    private var maxScore: Int = 100
    private var animatedProgress: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        color = Color.parseColor("#EFF6FF")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2563EB")
    }

    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val maxScoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val oval = RectF()

    fun setScore(score: Int, maxScore: Int, animate: Boolean = true) {
        this.score = score
        this.maxScore = maxScore.coerceAtLeast(1)
        val targetProgress = (score.toFloat() / this.maxScore).coerceIn(0f, 1f)

        if (animate) {
            val animator = ValueAnimator.ofFloat(0f, targetProgress).apply {
                duration = 1000
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animatedProgress = it.animatedValue as Float
                    invalidate()
                }
            }
            animator.start()
        } else {
            animatedProgress = targetProgress
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val stroke = progressPaint.strokeWidth
        val padding = stroke / 2f + 12f
        val diameter = minOf(w, h).toFloat()
        val left = (w - diameter) / 2f + padding
        val top = (h - diameter) / 2f + padding
        val right = left + diameter - (padding * 2)
        val bottom = top + diameter - (padding * 2)
        oval.set(left, top, right, bottom)

        scoreTextPaint.textSize = diameter * 0.28f
        maxScoreTextPaint.textSize = diameter * 0.11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background track ring
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        // Draw animated progress arc starting from top (-90 degrees)
        val sweepAngle = animatedProgress * 360f
        canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)

        // Draw score text
        val centerX = oval.centerX()
        val centerY = oval.centerY()
        val currentScore = (animatedProgress * maxScore).toInt()

        canvas.drawText("$currentScore", centerX, centerY + (scoreTextPaint.textSize * 0.15f), scoreTextPaint)
        canvas.drawText("/ $maxScore", centerX, centerY + (scoreTextPaint.textSize * 0.65f), maxScoreTextPaint)
    }
}
