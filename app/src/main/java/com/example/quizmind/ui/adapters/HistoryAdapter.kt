package com.example.quizmind.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.quizmind.R
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.QuizSourceType
import com.example.quizmind.databinding.ItemHistoryBinding
import com.example.quizmind.utils.HistoryDisplayHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onMenuClick: (HistoryItem) -> Unit,
    private val onReviewClick: (HistoryItem) -> Unit,
    private val onPlayClick: (HistoryItem) -> Unit = onReviewClick
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(Diff) {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.tvTitle.text = item.title
            binding.tvQuestionCount.text = "${item.totalQuestions} Qs"
            binding.tvDifficulty.text = item.difficulty.ifBlank { "Mixed" }
            binding.tvSourceType.text = QuizSourceType.displayLabel(item.sourceType)
            binding.ivSourceType.setImageResource(HistoryDisplayHelper.sourceTypeIconRes(item.sourceType))
            binding.ivIcon.setImageResource(HistoryDisplayHelper.subjectIconRes(item.subject))
            binding.ivMenu.setOnClickListener { onMenuClick(item) }

            if (item.isCompleted) {
                binding.tvScorePercent.visibility = View.VISIBLE
                binding.tvScorePercent.text = "${item.accuracyPercentage}%"
                binding.tvScorePercent.setBackgroundResource(R.drawable.bg_score_pill)
                binding.tvScorePercent.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.white)
                )
                binding.tvScore.text = "${item.finalScore} / ${item.maxScore}"
                binding.tvMeta.text = dateFormat.format(Date(item.completedAt))
                binding.btnReview.text = "Review →"
                binding.btnReview.isClickable = true
                binding.btnReview.isFocusable = true
                binding.btnReview.setOnClickListener { onReviewClick(item) }
            } else {
                binding.tvScorePercent.visibility = View.VISIBLE
                binding.tvScorePercent.text = "Saved"
                binding.tvScorePercent.setBackgroundResource(R.drawable.bg_qs_chip)
                binding.tvScorePercent.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.app_color)
                )
                binding.tvScore.text = "Downloaded"
                binding.tvMeta.text = "Downloaded · ${dateFormat.format(Date(item.completedAt))}"
                binding.btnReview.text = "Play →"
                binding.btnReview.isClickable = true
                binding.btnReview.isFocusable = true
                binding.btnReview.setOnClickListener { onPlayClick(item) }
            }

            binding.root.setOnClickListener(null)
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.completionId == newItem.completionId
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
