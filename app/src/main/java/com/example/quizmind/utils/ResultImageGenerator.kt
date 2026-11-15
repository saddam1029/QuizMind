package com.example.quizmind.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.quizmind.data.model.QuizResult
import java.io.File
import java.io.FileOutputStream

object ResultImageGenerator {

    fun generateResultBitmap(result: QuizResult): Bitmap {
        val width = 1080
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // Card Container
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val cardRect = RectF(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat())
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBorderPaint)

        // Brand Title
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("QuizMind", width / 2f, 150f, brandPaint)

        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ASSESSMENT SUMMARY", width / 2f, 195f, subBrandPaint)

        // Title: Quiz Complete!
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Quiz Complete!", width / 2f, 290f, titlePaint)

        // Topic & Subject
        val topicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${result.topic} • ${result.subject} • ${result.difficulty}",
            width / 2f,
            345f,
            topicPaint
        )

        // Circular Score Ring
        val ringCenterX = width / 2f
        val ringCenterY = 530f
        val ringRadius = 140f

        val ringBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.STROKE
            strokeWidth = 26f
        }
        canvas.drawCircle(ringCenterX, ringCenterY, ringRadius, ringBgPaint)

        val ringProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            style = Paint.Style.STROKE
            strokeWidth = 26f
            strokeCap = Paint.Cap.ROUND
        }
        val sweepAngle = ((result.finalScore.toFloat() / result.maxScore.coerceAtLeast(1)) * 360f).coerceIn(0f, 360f)
        val ringRect = RectF(
            ringCenterX - ringRadius,
            ringCenterY - ringRadius,
            ringCenterX + ringRadius,
            ringCenterY + ringRadius
        )
        canvas.drawArc(ringRect, -90f, sweepAngle, false, ringProgressPaint)

        // Score Text inside ring
        val scoreNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${result.finalScore}", ringCenterX, ringCenterY + 15f, scoreNumberPaint)

        val scoreMaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("/ ${result.maxScore}", ringCenterX, ringCenterY + 65f, scoreMaxPaint)

        val finalScoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Final score", ringCenterX, ringCenterY + 195f, finalScoreLabelPaint)

        // 3 Summary Chips (Correct, Wrong, Skipped)
        val chipY = 780f
        val chipWidth = 260f
        val chipHeight = 110f
        val chipSpacing = 30f
        val totalChipsWidth = (3 * chipWidth) + (2 * chipSpacing)
        var startX = (width - totalChipsWidth) / 2f

        val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val chipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Correct Chip
        drawChip(
            canvas,
            RectF(startX, chipY, startX + chipWidth, chipY + chipHeight),
            "✓ ${result.correctCount}",
            "Correct",
            Color.parseColor("#16A34A"),
            chipBgPaint,
            chipBorder
        )

        // Wrong Chip
        startX += chipWidth + chipSpacing
        drawChip(
            canvas,
            RectF(startX, chipY, startX + chipWidth, chipY + chipHeight),
            "✕ ${result.wrongCount}",
            "Wrong",
            Color.parseColor("#DC2626"),
            chipBgPaint,
            chipBorder
        )

        // Skipped Chip
        startX += chipWidth + chipSpacing
        drawChip(
            canvas,
            RectF(startX, chipY, startX + chipWidth, chipY + chipHeight),
            "− ${result.skippedCount}",
            "Skipped",
            Color.parseColor("#64748B"),
            chipBgPaint,
            chipBorder
        )

        // Performance Breakdown Heading
        val breakdownHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("Performance Breakdown", 120f, 960f, breakdownHeadingPaint)

        // 2x2 Performance Grid
        val gridY = 990f
        val gridCardW = 400f
        val gridCardH = 130f
        val gridGapX = 40f
        val gridGapY = 25f
        val gridStartX = 120f

        val gridBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }

        // Correct Marks
        drawMetricCard(
            canvas,
            RectF(gridStartX, gridY, gridStartX + gridCardW, gridY + gridCardH),
            "⊕ Correct Marks",
            QuizScoreCalculator.formatSignedMarks(result.correctMarks),
            Color.parseColor("#16A34A"),
            gridBgPaint,
            chipBorder
        )

        // Negative Marks
        drawMetricCard(
            canvas,
            RectF(gridStartX + gridCardW + gridGapX, gridY, gridStartX + (2 * gridCardW) + gridGapX, gridY + gridCardH),
            "⊖ Negative Marks",
            QuizScoreCalculator.formatMarks(result.negativeMarks),
            Color.parseColor("#DC2626"),
            gridBgPaint,
            chipBorder
        )

        // Accuracy
        val row2Y = gridY + gridCardH + gridGapY
        drawMetricCard(
            canvas,
            RectF(gridStartX, row2Y, gridStartX + gridCardW, row2Y + gridCardH),
            "% Accuracy",
            "${result.accuracyPercentage}%",
            Color.parseColor("#0F172A"),
            gridBgPaint,
            chipBorder
        )

        // Time Taken
        drawMetricCard(
            canvas,
            RectF(gridStartX + gridCardW + gridGapX, row2Y, gridStartX + (2 * gridCardW) + gridGapX, row2Y + gridCardH),
            "⏱ Time Taken",
            result.formattedTime,
            Color.parseColor("#0F172A"),
            gridBgPaint,
            chipBorder
        )

        // Footer note
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated & verified by QuizMind AI", width / 2f, 1370f, footerPaint)

        return bitmap
    }

    private fun drawChip(
        canvas: Canvas,
        rect: RectF,
        topText: String,
        bottomText: String,
        topColor: Int,
        bgPaint: Paint,
        borderPaint: Paint
    ) {
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = topColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(topText, rect.centerX(), rect.centerY() - 6f, topPaint)

        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(bottomText, rect.centerX(), rect.centerY() + 34f, bottomPaint)
    }

    private fun drawMetricCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        valueColor: Int,
        bgPaint: Paint,
        borderPaint: Paint
    ) {
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(label, rect.left + 24f, rect.top + 45f, labelPaint)

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = valueColor
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(value, rect.left + 24f, rect.top + 98f, valPaint)
    }

    fun shareResult(context: Context, result: QuizResult) {
        val bitmap = generateResultBitmap(result)
        val shareDir = File(context.cacheDir, "shared_results").apply {
            if (!exists()) mkdirs()
        }
        val file = File(shareDir, "QuizResult_${result.quizId}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "QuizMind Result: ${result.topic}")
            putExtra(
                Intent.EXTRA_TEXT,
                "I just scored ${result.finalScore}/${result.maxScore} (${result.accuracyPercentage}%) on \"${result.topic}\" quiz using QuizMind!"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Quiz Result"))
    }
}
