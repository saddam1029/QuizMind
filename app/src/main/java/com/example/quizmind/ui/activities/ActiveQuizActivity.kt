package com.example.quizmind.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.quizmind.R
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.databinding.ActivityActiveQuizBinding
import com.example.quizmind.ui.viewmodel.ActiveQuizViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class ActiveQuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUIZ = "extra_quiz"
        private const val TAG = "ActiveQuizActivity"
    }

    private lateinit var binding: ActivityActiveQuizBinding
    private val viewModel: ActiveQuizViewModel by viewModels()

    private inner class OptionViews(
        val layout: LinearLayout,
        val tvLetter: TextView,
        val tvText: TextView,
        val ivCheck: ImageView
    )

    private lateinit var optionViewsList: List<OptionViews>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityActiveQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        @Suppress("DEPRECATION")
        val quiz = intent.getSerializableExtra(EXTRA_QUIZ) as? Quiz
        if (quiz == null || quiz.questions.isEmpty()) {
            Log.e(TAG, "Quiz data is null or empty in intent.")
            Toast.makeText(this, "Quiz data is missing or corrupted.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "ActiveQuizActivity started with quiz: '${quiz.topic}', questionsCount=${quiz.questions.size}")

        setupOptionViews()
        setupListeners()
        setupBackHandler()

        viewModel.initializeQuiz(quiz)
        binding.tvQuizTopic.text = quiz.topic

        observeViewModel()
    }

    private fun setupOptionViews() {
        optionViewsList = listOf(
            OptionViews(binding.layoutOptionA, binding.tvLetterA, binding.tvOptionTextA, binding.ivCheckA),
            OptionViews(binding.layoutOptionB, binding.tvLetterB, binding.tvOptionTextB, binding.ivCheckB),
            OptionViews(binding.layoutOptionC, binding.tvLetterC, binding.tvOptionTextC, binding.ivCheckC),
            OptionViews(binding.layoutOptionD, binding.tvLetterD, binding.tvOptionTextD, binding.ivCheckD)
        )
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            showExitConfirmationDialog()
        }

        binding.btnCloseQuiz.setOnClickListener {
            showExitConfirmationDialog()
        }

        optionViewsList.forEach { optionView ->
            optionView.layout.setOnClickListener {
                val selectedText = optionView.tvText.text.toString()
                if (selectedText.isNotEmpty()) {
                    viewModel.selectOption(selectedText)
                }
            }
        }

        binding.btnHint.setOnClickListener {
            showHintBottomSheet()
        }

        binding.btnSkip.setOnClickListener {
            val currentIndex = viewModel.currentQuestionIndex.value
            val isLast = viewModel.isLastQuestion()
            Log.d(TAG, "btnSkip clicked on question #$currentIndex (isLast=$isLast)")
            if (isLast) {
                binding.btnNext.isEnabled = false
                binding.btnSkip.isEnabled = false
            }
            viewModel.skipCurrentQuestion()
        }

        binding.btnNext.setOnClickListener {
            val currentIndex = viewModel.currentQuestionIndex.value
            val isLast = viewModel.isLastQuestion()
            Log.d(TAG, "btnNext clicked on question #$currentIndex (isLast=$isLast)")
            if (isLast) {
                // Disable temporarily to prevent multiple rapid taps from launching duplicate screens
                binding.btnNext.isEnabled = false
                binding.btnSkip.isEnabled = false
            }
            val moved = viewModel.nextQuestion()
            if (!moved) {
                // User hasn't selected an option and hasn't skipped
                binding.btnNext.isEnabled = true
                binding.btnSkip.isEnabled = true
                Toast.makeText(this, "Please select an answer or tap Skip to continue.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentQuestionIndex.collect { index ->
                        displayQuestion(index)
                    }
                }

                launch {
                    viewModel.selectedAnswer.collect { selected ->
                        highlightSelectedOption(selected)
                    }
                }

                launch {
                    viewModel.elapsedTimeSeconds.collect { seconds ->
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        binding.tvTimer.text = String.format(Locale.US, "%02d:%02d", minutes, secs)
                    }
                }

                launch {
                    viewModel.navigationEvent.collect { result ->
                        Log.d(TAG, "Received navigationEvent: Navigating to QuizResultActivity for quizId=${result.quizId}")
                        val intent = Intent(this@ActiveQuizActivity, QuizResultActivity::class.java).apply {
                            putExtra(QuizResultActivity.EXTRA_QUIZ_RESULT, result)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun displayQuestion(index: Int) {
        val question = viewModel.getCurrentQuestion() ?: return
        val total = viewModel.getTotalQuestions()

        binding.tvQuestionProgress.text = "Question ${index + 1} of $total"
        val percentage = ((index + 1) * 100) / total.coerceAtLeast(1)
        binding.tvPercentageProgress.text = "$percentage% Complete"
        binding.progressBarQuiz.progress = percentage

        binding.tvQuestionText.text = question.question

        // Bind 4 options
        optionViewsList.forEachIndexed { optIndex, optionView ->
            val optText = question.options.getOrElse(optIndex) { "" }
            optionView.tvText.text = optText
        }

        // Update button text on last question
        if (index == total - 1) {
            binding.btnNext.text = "Finish Quiz"
        } else {
            binding.btnNext.text = "Next"
        }
        binding.btnNext.isEnabled = true
        binding.btnSkip.isEnabled = true
    }

    private fun highlightSelectedOption(selectedAnswer: String?) {
        optionViewsList.forEach { optionView ->
            val optText = optionView.tvText.text.toString()
            val isSelected = !selectedAnswer.isNullOrBlank() && optText.equals(selectedAnswer, ignoreCase = true)

            if (isSelected) {
                optionView.layout.setBackgroundResource(R.drawable.bg_option_selected)
                optionView.tvLetter.setBackgroundResource(R.drawable.bg_circle_letter_selected)
                optionView.tvLetter.setTextColor(Color.WHITE)
                optionView.tvText.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                optionView.tvText.setTypeface(null, Typeface.BOLD)

                optionView.ivCheck.setImageResource(R.drawable.ic_check)
                ImageViewCompat.setImageTintList(
                    optionView.ivCheck,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_color))
                )
            } else {
                optionView.layout.setBackgroundResource(R.drawable.bg_option_unselected)
                optionView.tvLetter.setBackgroundResource(R.drawable.bg_circle_letter_unselected)
                optionView.tvLetter.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                optionView.tvText.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                optionView.tvText.setTypeface(null, Typeface.NORMAL)

                optionView.ivCheck.setImageResource(R.drawable.bg_circle_radio_unselected)
                ImageViewCompat.setImageTintList(optionView.ivCheck, null)
            }
        }
    }

    private fun showHintBottomSheet() {
        val question = viewModel.getCurrentQuestion() ?: return
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_hint_bottom_sheet, null)
        dialog.setContentView(dialogView)

        val tvHintContent = dialogView.findViewById<TextView>(R.id.tvHintContent)
        val btnGotIt = dialogView.findViewById<MaterialButton>(R.id.btnGotIt)

        val hint = question.hint.ifBlank {
            "Consider key educational concepts related to this topic."
        }
        tvHintContent.text = hint

        btnGotIt.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Active Quiz?")
            .setMessage("Are you sure you want to exit? Your current quiz progress and answers will be lost.")
            .setPositiveButton("Exit Quiz") { _, _ ->
                Log.d(TAG, "User confirmed exit from ActiveQuizActivity.")
                viewModel.stopTimer()
                finish()
            }
            .setNegativeButton("Keep Playing", null)
            .show()
    }
}
