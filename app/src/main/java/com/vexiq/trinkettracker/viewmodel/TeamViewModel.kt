package com.vexiq.trinkettracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vexiq.trinkettracker.data.Team
import com.vexiq.trinkettracker.data.TeamDatabase
import com.vexiq.trinkettracker.data.TeamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProgressState(
    val collected: Int = 0,
    val total: Int = 0
) {
    val percentage: Int get() = if (total == 0) 0 else (collected * 100 / total)
    val fraction: Float get() = if (total == 0) 0f else collected.toFloat() / total.toFloat()
}

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class TeamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TeamRepository

    // Search queries
    private val _notCollectedSearch = MutableStateFlow("")
    val notCollectedSearch: StateFlow<String> = _notCollectedSearch.asStateFlow()

    private val _collectedSearch = MutableStateFlow("")
    val collectedSearch: StateFlow<String> = _collectedSearch.asStateFlow()

    // UI state (loading/error)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Progress
    private val _progress = MutableStateFlow(ProgressState())
    val progress: StateFlow<ProgressState> = _progress.asStateFlow()

    // Raw team flows from DB
    private val notCollectedRaw: StateFlow<List<Team>>
    private val collectedRaw: StateFlow<List<Team>>

    // Filtered by search
    @OptIn(ExperimentalCoroutinesApi::class)
    val notCollectedTeams: StateFlow<List<Team>>

    @OptIn(ExperimentalCoroutinesApi::class)
    val collectedTeams: StateFlow<List<Team>>

    init {
        val dao = TeamDatabase.getDatabase(application).teamDao()
        repository = TeamRepository(dao)

        notCollectedRaw = repository.getNotCollectedTeams()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        collectedRaw = repository.getCollectedTeams()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        notCollectedTeams = notCollectedSearch.flatMapLatest { query ->
            repository.getNotCollectedTeams().map { teams ->
                if (query.isBlank()) teams
                else teams.filter {
                    it.teamNumber.contains(query, ignoreCase = true) ||
                            it.teamName.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        collectedTeams = collectedSearch.flatMapLatest { query ->
            repository.getCollectedTeams().map { teams ->
                if (query.isBlank()) teams
                else teams.filter {
                    it.teamNumber.contains(query, ignoreCase = true) ||
                            it.teamName.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Update progress whenever teams change
        viewModelScope.launch {
            repository.getAllTeams().collect { all ->
                val total = all.size
                val collected = all.count { it.isCollected }
                _progress.value = ProgressState(collected = collected, total = total)
            }
        }
    }

    fun setNotCollectedSearch(query: String) {
        _notCollectedSearch.value = query
    }

    fun setCollectedSearch(query: String) {
        _collectedSearch.value = query
    }

    fun refreshTeams() {
        viewModelScope.launch {
            _uiState.value = UiState(isLoading = true)
            val result = repository.refreshTeams()
            result.fold(
                onSuccess = { count ->
                    _uiState.value = UiState(successMessage = "Loaded $count teams")
                },
                onFailure = { e ->
                    _uiState.value = UiState(error = e.message ?: "Unknown error")
                }
            )
        }
    }

    fun collectTeam(teamNumber: String, photoPath: String) {
        viewModelScope.launch {
            repository.markTeamCollected(teamNumber, photoPath)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
