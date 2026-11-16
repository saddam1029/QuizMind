package com.example.quizmind.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.session.QuizSessionStore
import com.example.quizmind.databinding.ActivityMainBinding
import com.example.quizmind.model.RecentQuiz
import com.example.quizmind.ui.adapters.RecentQuizAdapter
import com.example.quizmind.ui.viewmodel.HomeViewModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecentQuizAdapter
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
        adapter = RecentQuizAdapter(
            onItemClick = { quiz ->
                openHistoryResult(quiz.id)
            },
            onMenuClick = { _ ->
                startActivity(Intent(this, HistoryActivity::class.java))
            }
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
                    viewModel.recentHistory.collect { items ->
                        val recentList = items.take(5).map { it.toRecentQuiz() }
                        adapter.submitList(recentList)
                        val empty = recentList.isEmpty()
                        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                        binding.rvRecentQuizzes.visibility = if (empty) View.GONE else android.view.View.VISIBLE
                    }
                }
            }
        }
    }

    private fun openCreate(sourceType: String) {
        startActivity(
            Intent(this, CreateQuizActivity::class.java).apply {
                putExtra("source_type", sourceType)
            }
        )
    }

    private fun openHistoryResult(completionId: String) {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun HistoryItem.toRecentQuiz(): RecentQuiz {
        return RecentQuiz(
            id = completionId,
            title = title,
            questionInfo = subtitle,
            score = if (isCompleted) scoreLabel else "Downloaded",
            iconResId = com.example.quizmind.utils.HistoryDisplayHelper.subjectIconRes(subject)
        )
    }
}
