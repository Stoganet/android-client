package com.stoganet.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.api.model.LibraryItem
import com.stoganet.core.data.net.SearchApiException
import com.stoganet.core.data.search.SearchRepository
import com.stoganet.tv.StoganetApp
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 400L

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: SearchRepository) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Empty())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow.debounce(DEBOUNCE_MS.milliseconds).collect { runSearch(it) }
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                queryFlow.value = intent.text
                if (intent.text.isBlank()) _state.update { SearchUiState.Empty(intent.text) }
            }

            SearchIntent.Submit -> {
                val query = queryFlow.value
                if (query.length >= MIN_QUERY_LENGTH) {
                    viewModelScope.launch { runSearch(query) }
                }
            }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.length < MIN_QUERY_LENGTH) return
        val stateBeforeCall = _state.value
        _state.update { SearchUiState.Loading(query) }
        repository.search(query)
            .onSuccess { items ->
                if (queryFlow.value != query) return@onSuccess
                _state.update {
                    if (items.isEmpty()) {
                        SearchUiState.NoResults(query)
                    } else {
                        SearchUiState.Results(query, items.map { it.toUiState() }.toImmutableList())
                    }
                }
            }
            .onFailure { error ->
                if (queryFlow.value != query) return@onFailure
                if ((error as? SearchApiException)?.status == HttpStatusCode.TooManyRequests) {
                    _state.update { stateBeforeCall }
                    return@onFailure
                }
                _state.update { SearchUiState.Error(query) }
            }
    }

    private fun LibraryItem.toUiState() = SearchResultUiState(
        id = id,
        posterUrl = poster,
        contentDescription = "$title ($year)",
    )

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                SearchViewModel(app.services.searchRepository)
            }
        }
    }
}
