package com.example.quizmind.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.quizmind.R
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onMenuClick: (HistoryItem) -> Unit
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
            binding.tvSourceType.text = "Topic"
            binding.ivSourceType.setImageResource(R.drawable.ic_topic_small)
            binding.tvScorePercent.text = "${item.accuracyPercentage}%"
            binding.tvScore.text = "${item.finalScore} / ${item.maxScore}"
            binding.tvMeta.text = dateFormat.format(Date(item.completedAt))
            binding.ivIcon.setImageResource(R.drawable.ic_biology)

            binding.root.setOnClickListener { onMenuClick(item) }
            binding.btnReview.setOnClickListener { onItemClick(item) }
            binding.ivMenu.setOnClickListener { onMenuClick(item) }

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
