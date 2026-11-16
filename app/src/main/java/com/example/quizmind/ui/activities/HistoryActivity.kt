package com.example.quizmind.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quizmind.R
import com.example.quizmind.data.mapper.toQuizResultSummary
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.databinding.ActivityHistoryBinding
import com.example.quizmind.ui.adapters.HistoryAdapter
import com.example.quizmind.ui.viewmodel.HistoryUiState
import com.example.quizmind.ui.viewmodel.HistoryViewModel
import com.example.quizmind.utils.ResultImageGenerator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    @Inject
    lateinit var quizSessionStore: QuizSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        adapter = HistoryAdapter(
            onMenuClick = { item -> showMenu(item) },
            onReviewClick = { item -> openResultSummary(item) },
            onPlayClick = { item -> playDownloadedQuiz(item) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.ivSearch.setOnClickListener {
            openSearch()
        }

        binding.ivBack.setOnClickListener {
            if (binding.etSearch.visibility == View.VISIBLE) {
                closeSearch()
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.etSearch.visibility == View.VISIBLE) {
                    closeSearch()
                } else {
                    finish()
                }
            }
        })

        @SuppressLint("ClickableViewAccessibility")
        binding.etSearch.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = binding.etSearch.compoundDrawablesRelative[2]
                    ?: binding.etSearch.compoundDrawables[2]
                if (drawableEnd != null) {
                    val width = drawableEnd.intrinsicWidth.takeIf { it > 0 } ?: 48
                    val extraTapArea = 32
                    val rightBound = binding.etSearch.right - binding.etSearch.paddingRight
                    val leftBound = rightBound - width - extraTapArea
                    if (event.rawX >= leftBound) {
                        closeSearch()
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString().orEmpty())
            }
        })

        observeState()
    }

    private fun openSearch() {
        binding.ivSearch.visibility = View.GONE
        binding.tvHistory.visibility = View.GONE
        binding.ivBack.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        binding.etSearch.setText("")
        binding.etSearch.visibility = View.GONE
        binding.ivSearch.visibility = View.VISIBLE
        binding.tvHistory.visibility = View.VISIBLE
        binding.ivBack.visibility = View.VISIBLE
        viewModel.search("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HistoryUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is HistoryUiState.Ready -> {
                            binding.progressBar.visibility = View.GONE
                            adapter.submitList(state.items)
                            val empty = state.items.isEmpty()
                            binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                            binding.rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
                        }
                        is HistoryUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@HistoryActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showMenu(item: HistoryItem) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_history_menu, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvMenuTitle).text = item.title
        view.findViewById<TextView>(R.id.tvMenuSubtitle).text = if (item.isCompleted) {
            "${item.totalQuestions} Questions · ${item.difficulty.ifBlank { "Mixed" }} · Latest score: ${item.finalScore}/${item.maxScore}"
        } else {
            "${item.totalQuestions} Questions · ${item.difficulty.ifBlank { "Mixed" }} · Downloaded"
        }

        val btnDownload = view.findViewById<View>(R.id.btnDownload)
        val tvDownloadLabel = view.findViewById<TextView>(R.id.tvDownloadLabel)
        val tvDownloadMeta = view.findViewById<TextView>(R.id.tvDownloadMeta)
        val btnGiveAgain = view.findViewById<View>(R.id.btnGiveAgain)
        val btnReview = view.findViewById<View>(R.id.btnReview)
        val btnShare = view.findViewById<View>(R.id.btnShare)

        if (item.isDownloaded) {
            tvDownloadLabel.text = "Already Downloaded"
            tvDownloadMeta.text = "Saved"
            btnDownload.isEnabled = false
            btnDownload.alpha = 0.5f
        }

        view.findViewById<View>(R.id.btnCloseMenu).setOnClickListener { dialog.dismiss() }

        if (item.isCompleted) {
            btnReview.visibility = View.VISIBLE
            btnShare.visibility = View.VISIBLE
            btnReview.setOnClickListener {
                dialog.dismiss()
                openResultSummary(item)
            }
        } else {
            btnReview.visibility = View.GONE
            btnShare.visibility = View.GONE
        }

        btnGiveAgain.setOnClickListener {
            dialog.dismiss()
            playDownloadedQuiz(item)
        }

        btnDownload.setOnClickListener {
            dialog.dismiss()
            viewModel.download(item) { result ->
                result.onSuccess {
                    Toast.makeText(this, "Quiz downloaded for offline play.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, it.localizedMessage ?: "Download failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnShare.setOnClickListener {
            dialog.dismiss()
            if (!item.isCompleted) return@setOnClickListener
            try {
                ResultImageGenerator.shareResult(this, item.toQuizResultSummary())
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage ?: "Share failed.", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item)
        }

        view.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteDialog(item)
        }

        dialog.show()
    }

    private fun showRenameDialog(item: HistoryItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rename_history, null)
        val input = view.findViewById<EditText>(R.id.etRename)
        input.setText(item.title)
        input.setSelection(input.text.length)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnCancelRename).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSaveRename).setOnClickListener {
            dialog.dismiss()
            viewModel.rename(item, input.text.toString()) { result ->
                result.onSuccess {
                    Toast.makeText(this, "Quiz renamed.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, it.localizedMessage ?: "Rename failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showDeleteDialog(item: HistoryItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_history, null)
        view.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "${item.title} and its result will be permanently removed."

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            viewModel.delete(item) { result ->
                result.onSuccess {
                    Toast.makeText(this, "History item deleted.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, it.localizedMessage ?: "Delete failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun openResultSummary(item: HistoryItem) {
        if (!item.isCompleted) {
            playDownloadedQuiz(item)
            return
        }
        val result = item.toQuizResultSummary()
        quizSessionStore.putResult(result)
        startActivity(
            Intent(this, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_RESULT_ID, result.completionId)
            }
        )
    }

    private fun playDownloadedQuiz(item: HistoryItem) {
        lifecycleScope.launch {
            val quiz = viewModel.prepareReplay(item)
            if (quiz == null) {
                Toast.makeText(
                    this@HistoryActivity,
                    "Download this quiz first to play it offline.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startActivity(
                    Intent(this@HistoryActivity, ActiveQuizActivity::class.java).apply {
                        putExtra(ActiveQuizActivity.EXTRA_QUIZ_ID, quiz.id)
                    }
                )
            }
        }
    }
}
