package com.example.quizmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.credits.CreditPolicy
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quizRepository: QuizRepository
) : ViewModel() {

    val credits: StateFlow<Long> = quizRepository.observeCredits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditPolicy.STARTING_CREDITS)

    private val _recentHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val recentHistory: StateFlow<List<HistoryItem>> = _recentHistory.asStateFlow()

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            try {
                val result = quizRepository.loadMergedHistory()
                _recentHistory.value = result.getOrElse {
                    quizRepository.getLocalHistorySnapshot()
                }
            } catch (_: Exception) {
                _recentHistory.value = runCatching {
                    quizRepository.getLocalHistorySnapshot()
                }.getOrDefault(emptyList())
            }
        }
    }
}
