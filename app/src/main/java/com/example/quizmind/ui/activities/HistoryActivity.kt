package com.example.quizmind.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import com.example.quizmind.data.mapper.toQuizResultSummary
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.databinding.ActivityHistoryBinding
import com.example.quizmind.ui.adapters.HistoryAdapter
import com.example.quizmind.ui.history.HistoryItemMenuHelper
import com.example.quizmind.ui.viewmodel.HistoryUiState
import com.example.quizmind.ui.viewmodel.HistoryViewModel
import com.facebook.shimmer.ShimmerFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private lateinit var historyMenuHelper: HistoryItemMenuHelper

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

        historyMenuHelper = HistoryItemMenuHelper(
            activity = this,
            callbacks = object : HistoryItemMenuHelper.Callbacks {
                override fun onDownload(item: HistoryItem, onDone: (Result<Unit>) -> Unit) {
                    viewModel.download(item, onDone)
                }

                override fun onRename(item: HistoryItem, newTitle: String, onDone: (Result<Unit>) -> Unit) {
                    viewModel.rename(item, newTitle, onDone)
                }

                override fun onDelete(item: HistoryItem, onDone: (Result<Unit>) -> Unit) {
                    viewModel.delete(item, onDone)
                }

                override fun onOpenResult(item: HistoryItem) {
                    openResultSummary(item)
                }

                override fun onPlay(item: HistoryItem) {
                    playDownloadedQuiz(item)
                }
            }
        )

        adapter = HistoryAdapter(
            onMenuClick = { item -> historyMenuHelper.show(item) },
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

    private fun historyShimmer(): ShimmerFrameLayout {
        return binding.layoutHistorySkeleton.root
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val shimmer = historyShimmer()
                    when (state) {
                        is HistoryUiState.Loading -> {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvHistory.visibility = View.GONE
                            shimmer.visibility = View.VISIBLE
                            shimmer.startShimmer()
                        }
                        is HistoryUiState.Ready -> {
                            shimmer.stopShimmer()
                            shimmer.visibility = View.GONE
                            adapter.submitList(state.items)
                            val empty = state.items.isEmpty()
                            binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                            binding.rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
                        }
                        is HistoryUiState.Error -> {
                            shimmer.stopShimmer()
                            shimmer.visibility = View.GONE
                            val cached = state.cachedItems
                            adapter.submitList(cached)
                            if (cached.isNotEmpty()) {
                                binding.layoutEmpty.visibility = View.GONE
                                binding.rvHistory.visibility = View.VISIBLE
                            } else {
                                binding.rvHistory.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.VISIBLE
                            }
                            Toast.makeText(this@HistoryActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            try {
                historyShimmer().stopShimmer()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
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
