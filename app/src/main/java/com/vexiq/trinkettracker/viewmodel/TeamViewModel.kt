package com.vexiq.trinkettracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vexiq.trinkettracker.data.RefreshResult
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
    val percentage: Int  get() = if (total == 0) 0 else (collected * 100 / total)
    val fraction: Float  get() = if (total == 0) 0f else collected.toFloat() / total.toFloat()
}

data class UiState(
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val show403Hint: Boolean = false
)

class TeamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TeamRepository

    private val _notCollectedSearch = MutableStateFlow("")
    val notCollectedSearch: StateFlow<String> = _notCollectedSearch.asStateFlow()

    private val _collectedSearch = MutableStateFlow("")
    val collectedSearch: StateFlow<String> = _collectedSearch.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(ProgressState())
    val progress: StateFlow<ProgressState> = _progress.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notCollectedTeams: StateFlow<List<Team>>

    @OptIn(ExperimentalCoroutinesApi::class)
    val collectedTeams: StateFlow<List<Team>>

    init {
        val dao = TeamDatabase.getDatabase(application).teamDao()
        repository = TeamRepository(dao, application.applicationContext)

        @OptIn(ExperimentalCoroutinesApi::class)
        notCollectedTeams = _notCollectedSearch.flatMapLatest { query ->
            repository.getNotCollectedTeams().map { teams ->
                if (query.isBlank()) teams
                else teams.filter {
                    it.teamNumber.contains(query, ignoreCase = true) ||
                            it.teamName.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        collectedTeams = _collectedSearch.flatMapLatest { query ->
            repository.getCollectedTeams().map { teams ->
                if (query.isBlank()) teams
                else teams.filter {
                    it.teamNumber.contains(query, ignoreCase = true) ||
                            it.teamName.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            repository.getAllTeams().collect { all ->
                _progress.value = ProgressState(
                    collected = all.count { it.isCollected },
                    total = all.size
                )
            }
        }
    }

    fun setNotCollectedSearch(query: String) { _notCollectedSearch.value = query }
    fun setCollectedSearch(query: String)    { _collectedSearch.value = query }

    fun refreshTeams() {
        viewModelScope.launch {
            _uiState.value = UiState(isLoading = true, show403Hint = _uiState.value.show403Hint)
            when (val result = repository.refreshTeams()) {
                is RefreshResult.Success ->
                    _uiState.value = UiState(successMessage = "Loaded ${result.count} teams")
                is RefreshResult.Failure ->
                    _uiState.value = UiState(error = result.message, show403Hint = result.is403)
            }
        }
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, error = null)
            repository.importXlsFromUri(uri).fold(
                onSuccess = { count ->
                    _uiState.value = UiState(successMessage = "Imported $count teams successfully!")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        error = e.message ?: "Import failed"
                    )
                }
            )
        }
    }

    fun collectTeam(teamNumber: String, photoPath: String) {
        viewModelScope.launch { repository.markTeamCollected(teamNumber, photoPath) }
    }

    fun retakeTeamPhoto(teamNumber: String, photoPath: String) {
        viewModelScope.launch { repository.retakeTeamPhoto(teamNumber, photoPath) }
    }

    fun removeTeams(teamNumbers: List<String>) {
        viewModelScope.launch { repository.removeTeams(teamNumbers) }
    }

    fun dismiss403Hint() {
        _uiState.value = _uiState.value.copy(show403Hint = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
