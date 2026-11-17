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

sealed class HomeHistoryUiState {
    object Loading : HomeHistoryUiState()
    data class Ready(val items: List<HistoryItem>) : HomeHistoryUiState()
    data class Error(val message: String, val cachedItems: List<HistoryItem> = emptyList()) : HomeHistoryUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quizRepository: QuizRepository
) : ViewModel() {

    val credits: StateFlow<Long> = quizRepository.observeCredits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditPolicy.STARTING_CREDITS)

    private val _historyUiState = MutableStateFlow<HomeHistoryUiState>(HomeHistoryUiState.Loading)
    val historyUiState: StateFlow<HomeHistoryUiState> = _historyUiState.asStateFlow()

    /** Kept for callers that only need the list; mirrors Ready items. */
    private val _recentHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val recentHistory: StateFlow<List<HistoryItem>> = _recentHistory.asStateFlow()

    private var hasCompletedInitialLoad = false

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            val cached = runCatching { quizRepository.getLocalHistorySnapshot() }
                .getOrDefault(emptyList())

            if (cached.isNotEmpty()) {
                // Room/offline cache available — show immediately, no skeleton flash.
                publishReady(cached)
            } else if (!hasCompletedInitialLoad) {
                _historyUiState.value = HomeHistoryUiState.Loading
            }

            try {
                val result = quizRepository.loadMergedHistory()
                result.onSuccess { items ->
                    publishReady(items)
                }.onFailure { error ->
                    if (cached.isNotEmpty()) {
                        publishReady(cached)
                    } else {
                        _historyUiState.value = HomeHistoryUiState.Error(
                            message = error.localizedMessage ?: "Failed to load history.",
                            cachedItems = emptyList()
                        )
                        _recentHistory.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                if (cached.isNotEmpty()) {
                    publishReady(cached)
                } else {
                    _historyUiState.value = HomeHistoryUiState.Error(
                        message = e.localizedMessage ?: "Failed to load history.",
                        cachedItems = emptyList()
                    )
                    _recentHistory.value = emptyList()
                }
            } finally {
                hasCompletedInitialLoad = true
            }
        }
    }

    private fun publishReady(items: List<HistoryItem>) {
        _recentHistory.value = items
        _historyUiState.value = HomeHistoryUiState.Ready(items)
    }
}
