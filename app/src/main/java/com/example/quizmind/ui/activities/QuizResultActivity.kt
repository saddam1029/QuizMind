package com.example.quizmind.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.quizmind.R
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.databinding.ActivityQuizResultBinding
import com.example.quizmind.ui.viewmodel.DownloadState
import com.example.quizmind.ui.viewmodel.QuizResultViewModel
import com.example.quizmind.utils.ResultImageGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QuizResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizResultBinding
    private val viewModel: QuizResultViewModel by viewModels()
    private var quizResult: QuizResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuizResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val resultId = intent.getStringExtra(EXTRA_RESULT_ID)
        quizResult = resultId?.let { viewModel.getResult(it) }
        val result = quizResult

        if (result == null) {
            Toast.makeText(this, "Result data could not be loaded.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        populateResultUI(result)
        setupListeners(result)
        viewModel.checkIsDownloaded(result.quizId)
        observeViewModel()
    }

    private fun populateResultUI(result: QuizResult) {
        binding.tvResultTopic.text = "${result.topic} • ${result.subject}"

        // Circular score indicator
        binding.circularScoreView.setScore(result.finalScore, result.maxScore, animate = true)

        // Summary chips
        binding.tvCorrectCount.text = result.correctCount.toString()
        binding.tvWrongCount.text = result.wrongCount.toString()
        binding.tvSkippedCount.text = result.skippedCount.toString()

        // Feedback message
        binding.tvFeedbackMessage.text = result.feedbackMessage

        // Performance breakdown
        binding.tvCorrectMarks.text = "+${result.correctMarks}"
        binding.tvNegativeMarks.text = result.negativeMarks.toString()
        binding.tvAccuracy.text = "${result.accuracyPercentage}%"
        binding.tvTimeTaken.text = result.formattedTime
    }

    private fun setupListeners(result: QuizResult) {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCloseResult.setOnClickListener {
            finish()
        }

        // Review Answers: Visible but disabled/not implemented as per prompt requirements
        binding.btnReviewAnswers.setOnClickListener {
            Toast.makeText(this, "Review Answers will be available in an upcoming update.", Toast.LENGTH_SHORT).show()
        }

        // Give Quiz Again: return to CreateQuizActivity with the same topic & config
        binding.btnGiveQuizAgain.setOnClickListener {
            val intent = Intent(this, CreateQuizActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("source_type", "topic")
                putExtra("prefill_topic", result.topic)
                putExtra("prefill_question_count", result.totalQuestions)
                putExtra("prefill_difficulty", result.difficulty)
            }
            startActivity(intent)
            finish()
        }

        // Generate New Quiz: return to quiz creation flow
        binding.btnGenerateNewQuiz.setOnClickListener {
            val intent = Intent(this, CreateQuizActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("source_type", "topic")
            }
            startActivity(intent)
            finish()
        }

        // Share result as an image
        binding.btnShare.setOnClickListener {
            try {
                ResultImageGenerator.shareResult(this, result)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to share results: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        // Download result to Room
        binding.btnDownload.setOnClickListener {
            viewModel.downloadQuiz(result)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isDownloaded.collect { downloaded ->
                        if (downloaded) {
                            binding.tvDownloadLabel.text = "Downloaded"
                            binding.ivDownloadIcon.setImageResource(R.drawable.ic_check)
                            binding.ivDownloadIcon.imageTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@QuizResultActivity, R.color.app_color)
                            )
                        } else {
                            binding.tvDownloadLabel.text = "Download"
                            binding.ivDownloadIcon.setImageResource(R.drawable.ic_download)
                            binding.ivDownloadIcon.imageTintList = ColorStateList.valueOf(
                                ContextCompat.getColor(this@QuizResultActivity, R.color.qm_text_primary)
                            )
                        }
                    }
                }

                launch {
                    viewModel.downloadState.collect { state ->
                        when (state) {
                            is DownloadState.Downloading -> {
                                binding.tvDownloadLabel.text = "Saving..."
                            }
                            is DownloadState.Success -> {
                                Toast.makeText(
                                    this@QuizResultActivity,
                                    "Quiz downloaded successfully for offline practice!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is DownloadState.AlreadyDownloaded -> {
                                Toast.makeText(
                                    this@QuizResultActivity,
                                    "This quiz is already saved offline.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is DownloadState.Error -> {
                                Toast.makeText(
                                    this@QuizResultActivity,
                                    state.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is DownloadState.Idle -> {}
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_ID = "extra_result_id"
    }
}
