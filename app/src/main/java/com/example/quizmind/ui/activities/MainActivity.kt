package com.example.quizmind.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.quizmind.R
import com.example.quizmind.data.mapper.toQuizResultSummary
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.databinding.ActivityMainBinding
import com.example.quizmind.ui.adapters.HistoryAdapter
import com.example.quizmind.ui.history.HistoryItemMenuHelper
import com.example.quizmind.ui.viewmodel.HomeHistoryUiState
import com.example.quizmind.ui.viewmodel.HomeViewModel
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HistoryAdapter
    private lateinit var historyMenuHelper: HistoryItemMenuHelper
    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var quizSessionStore: QuizSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
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

        setupUserData()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHistory()
    }

    private fun setupUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val name = currentUser.displayName
            if (!name.isNullOrEmpty()) {
                binding.tvUserName.text = name
            } else {
                val emailName = currentUser.email?.substringBefore("@")
                if (!emailName.isNullOrEmpty()) {
                    binding.tvUserName.text = emailName
                }
            }

            val photoUrl = currentUser.photoUrl
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onMenuClick = { item -> historyMenuHelper.show(item) },
            onReviewClick = { item -> openResultSummary(item) },
            onPlayClick = { item -> playDownloadedQuiz(item) }
        )

        binding.rvRecentQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvRecentQuizzes.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.cardTopic.setOnClickListener {
            openCreate("topic")
        }
        binding.cardTextNotes.setOnClickListener {
            openCreate("text")
        }
        binding.cardPdf.setOnClickListener {
            openCreate("pdf")
        }
        binding.cardScanNotes.setOnClickListener {
            openCreate("image")
        }
        binding.tvViewAll.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.layoutEmpty.findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            openCreate("topic")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.credits.collect { credits ->
                        binding.tvCreditsCount.text = credits.toString()
                    }
                }
                launch {
                    viewModel.historyUiState.collect { state ->
                        renderHistoryState(state)
                    }
                }
            }
        }
    }

    private fun renderHistoryState(state: HomeHistoryUiState) {
        val shimmer = historyShimmer()
        when (state) {
            is HomeHistoryUiState.Loading -> {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvRecentQuizzes.visibility = View.GONE
                shimmer.visibility = View.VISIBLE
                shimmer.startShimmer()
            }
            is HomeHistoryUiState.Ready -> {
                shimmer.stopShimmer()
                shimmer.visibility = View.GONE
                val recentList = state.items.take(5)
                adapter.submitList(recentList)
                val empty = recentList.isEmpty()
                binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvRecentQuizzes.visibility = if (empty) View.GONE else View.VISIBLE
            }
            is HomeHistoryUiState.Error -> {
                shimmer.stopShimmer()
                shimmer.visibility = View.GONE
                val cached = state.cachedItems.take(5)
                adapter.submitList(cached)
                if (cached.isNotEmpty()) {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvRecentQuizzes.visibility = View.VISIBLE
                } else {
                    binding.rvRecentQuizzes.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun historyShimmer(): ShimmerFrameLayout {
        return binding.layoutHistorySkeleton.root as ShimmerFrameLayout
    }

    override fun onDestroy() {
        historyShimmer().stopShimmer()
        super.onDestroy()
    }

    private fun openCreate(sourceType: String) {
        startActivity(
            Intent(this, CreateQuizActivity::class.java).apply {
                putExtra("source_type", sourceType)
            }
        )
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
                    this@MainActivity,
                    "Download this quiz first to play it offline.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startActivity(
                    Intent(this@MainActivity, ActiveQuizActivity::class.java).apply {
                        putExtra(ActiveQuizActivity.EXTRA_QUIZ_ID, quiz.id)
                    }
                )
            }
        }
    }
}
