package com.example.quizmind.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quizmind.R
import com.example.quizmind.databinding.FragmentHomeBinding
import com.example.quizmind.model.RecentQuiz
import com.example.quizmind.ui.adapters.RecentQuizAdapter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecentQuizAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        adapter = RecentQuizAdapter(
            onItemClick = { _ ->
                // Handle quiz click
            },
            onMenuClick = { _ ->
                // Handle quiz menu click
            }
        )

        binding.rvRecentQuizzes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentQuizzes.adapter = adapter

        val sampleQuizzes = listOf(
            RecentQuiz(
                id = "1",
                title = "Biology Basics",
                questionInfo = "10 questions · Mixed",
                score = "68/100",
                iconResId = R.drawable.ic_topic
            ),
            RecentQuiz(
                id = "2",
                title = "History of Pakistan",
                questionInfo = "15 questions · Mixed",
                score = "110/150",
                iconResId = R.drawable.ic_topic
            ),
            RecentQuiz(
                id = "3",
                title = "Kotlin Basics",
                questionInfo = "20 questions · Hard",
                score = "148/200",
                iconResId = R.drawable.ic_topic
            )
        )

        adapter.submitList(sampleQuizzes)
    }

    private fun setupClickListeners() {
        binding.cardTopic.setOnClickListener {
            // Topic action
        }
        binding.cardTextNotes.setOnClickListener {
            // Text/Notes action
        }
        binding.cardPdf.setOnClickListener {
            // PDF action
        }
        binding.cardScanNotes.setOnClickListener {
            // Scan Notes action
        }
        binding.tvViewAll.setOnClickListener {
            // View All action
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
