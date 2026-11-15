package com.example.quizmind.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
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
            onItemClick = { item -> openResultSummary(item) },
            onMenuClick = { item -> showMenu(item) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString().orEmpty())
            }
        })

        observeState()
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
        val btnDownload = view.findViewById<TextView>(R.id.btnDownload)
        val btnGiveAgain = view.findViewById<TextView>(R.id.btnGiveAgain)

        if (item.isDownloaded) {
            btnDownload.text = "Already Downloaded"
            btnDownload.isEnabled = false
        }

        btnGiveAgain.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val quiz = viewModel.prepareReplay(item)
                if (quiz == null) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Download this quiz first to play it again offline.",
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

        view.findViewById<TextView>(R.id.btnShare).setOnClickListener {
            dialog.dismiss()
            try {
                ResultImageGenerator.shareResult(this, item.toQuizResultSummary())
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage ?: "Share failed.", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<TextView>(R.id.btnRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item)
        }

        view.findViewById<TextView>(R.id.btnDelete).setOnClickListener {
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

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                viewModel.rename(item, input.text.toString()) { result ->
                    result.onSuccess {
                        Toast.makeText(this, "Quiz renamed.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this, it.localizedMessage ?: "Rename failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(item: HistoryItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_history, null)
        view.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "Delete \"${item.title}\" from history? This cannot be undone."

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete(item) { result ->
                    result.onSuccess {
                        Toast.makeText(this, "History item deleted.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this, it.localizedMessage ?: "Delete failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openResultSummary(item: HistoryItem) {
        val result = item.toQuizResultSummary()
        quizSessionStore.putResult(result)
        startActivity(
            Intent(this, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_RESULT_ID, result.completionId)
            }
        )
    }
}
