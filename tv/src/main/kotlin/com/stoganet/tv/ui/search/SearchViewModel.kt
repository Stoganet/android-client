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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 400L

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: SearchRepository) : ViewModel() {

    private sealed interface Request {
        val query: String

        data class Typed(override val query: String) : Request
        data class Submitted(override val query: String) : Request
    }

    private val requests = MutableSharedFlow<Request>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var query = ""

    // A cancelled search leaves its Loading behind, so fall back to the last state that was not one
    private var settled: SearchUiState = SearchUiState.Empty()

    val state: StateFlow<SearchUiState> = requests
        .flatMapLatest { request -> results(request) }
        .onEach { if (it !is SearchUiState.Loading) settled = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState.Empty())

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                query = intent.text
                requests.tryEmit(Request.Typed(intent.text))
            }

            SearchIntent.Submit -> if (query.length >= MIN_QUERY_LENGTH) requests.tryEmit(Request.Submitted(query))
        }
    }

    private fun results(request: Request): Flow<SearchUiState> = flow {
        when {
            request.query.isBlank() -> emit(SearchUiState.Empty(request.query))

            request.query.length < MIN_QUERY_LENGTH -> emit(settled)

            else -> {
                if (request is Request.Typed) delay(DEBOUNCE_MS.milliseconds)
                emit(SearchUiState.Loading(request.query))
                emit(search(request.query) ?: settled)
            }
        }
    }

    // Null on 429 so the screen keeps what it showed before the call
    private suspend fun search(query: String): SearchUiState? = repository.search(query).fold(
        onSuccess = { items ->
            if (items.isEmpty()) {
                SearchUiState.NoResults(query)
            } else {
                SearchUiState.Results(query, items.map { it.toUiState() }.toImmutableList())
            }
        },
        onFailure = { error ->
            if ((error as? SearchApiException)?.status == HttpStatusCode.TooManyRequests) {
                null
            } else {
                SearchUiState.Error(query)
            }
        },
    )

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
