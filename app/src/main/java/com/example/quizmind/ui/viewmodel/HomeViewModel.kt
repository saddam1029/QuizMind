package com.example.quizmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizmind.data.credits.CreditPolicy
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quizRepository: QuizRepository
) : ViewModel() {

    val credits: StateFlow<Long> = quizRepository.observeCredits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditPolicy.STARTING_CREDITS)

    val recentHistory: StateFlow<List<HistoryItem>> = quizRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try {
                quizRepository.syncPendingHistory()
                if (quizRepository.isOnline()) {
                    quizRepository.loadOnlineHistory()
                }
            } catch (_: Exception) {
                // Home must open even when Firestore is offline.
            }
        }
    }
}
