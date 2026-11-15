package com.example.quizmind.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.quizmind.R
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onMenuClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(Diff) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

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
            binding.tvSubtitle.text = item.subtitle
            binding.tvScore.text = item.scoreLabel
            val sourceLabel = if (item.source == QuizResult.SOURCE_DOWNLOADED) "Downloaded" else "Online"
            val dateLabel = dateFormat.format(Date(item.completedAt))
            binding.tvMeta.text = "${item.subject} · $sourceLabel · $dateLabel"
            binding.ivIcon.setImageResource(R.drawable.ic_computer)

            binding.root.setOnClickListener { onItemClick(item) }
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
