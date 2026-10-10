package com.example.quizmind.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quizmind.databinding.ItemRecentQuizBinding
import com.example.quizmind.model.RecentQuiz

class RecentQuizAdapter(
    private var quizzes: List<RecentQuiz> = emptyList(),
    private val onItemClick: ((RecentQuiz) -> Unit)? = null,
    private val onMenuClick: ((RecentQuiz) -> Unit)? = null,
) : RecyclerView.Adapter<RecentQuizAdapter.QuizViewHolder>() {

    fun submitList(newList: List<RecentQuiz>) {
        quizzes = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemRecentQuizBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuizViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        holder.bind(quizzes[position])
    }

    override fun getItemCount(): Int = quizzes.size

    inner class QuizViewHolder(
        private val binding: ItemRecentQuizBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(quiz: RecentQuiz) {
            binding.tvQuizTitle.text = quiz.title
            binding.tvQuizSubtitle.text = quiz.questionInfo
            binding.tvQuizScore.text = quiz.score
            binding.ivQuizIcon.setImageResource(quiz.iconResId)

            binding.root.setOnClickListener {
                onItemClick?.invoke(quiz)
            }

            binding.ivMenuDots.setOnClickListener {
                onMenuClick?.invoke(quiz)
            }
        }
    }
}
