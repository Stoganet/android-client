package com.stoganet.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.api.model.LibraryItem
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.library.LibraryRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LIMIT = 100

class LibraryViewModel(private val type: MediaType, private val repository: LibraryRepository) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var nextCursor: String? = null

    init {
        loadLibrary()
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            LibraryIntent.Retry -> {
                if (_state.value is LibraryUiState.Loading) return
                nextCursor = null
                loadLibrary()
            }

            LibraryIntent.LoadMore -> {
                val current = _state.value
                if (current !is LibraryUiState.Content || current.isLoadingMore || !current.hasMore) return
                loadLibrary(nextCursor)
            }
        }
    }

    private fun loadLibrary(cursor: String? = null) {
        if (cursor == null) {
            _state.update { LibraryUiState.Loading }
        } else {
            _state.update { current ->
                if (current is LibraryUiState.Content) {
                    current.copy(
                        isLoadingMore = true,
                        hasLoadMoreError = false,
                    )
                } else {
                    current
                }
            }
        }
        viewModelScope.launch {
            repository.getLibrary(type, cursor, LIMIT)
                .onSuccess { response ->
                    nextCursor = response.nextCursor
                    val newItems = response.items.map { it.toUiState() }
                    _state.update { current ->
                        val existing: ImmutableList<LibraryItemUiState> =
                            if (current is LibraryUiState.Content) current.items else persistentListOf()
                        LibraryUiState.Content(
                            items = (existing + newItems).toImmutableList(),
                            hasMore = response.nextCursor != null,
                            isLoadingMore = false,
                            hasLoadMoreError = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        if (current is LibraryUiState.Content) {
                            current.copy(isLoadingMore = false, hasLoadMoreError = true)
                        } else {
                            LibraryUiState.Error
                        }
                    }
                }
        }
    }

    private fun LibraryItem.toUiState() = LibraryItemUiState(
        id = id,
        posterUrl = poster,
        contentDescription = "$title ($year)",
    )

    companion object {
        fun factory(type: MediaType): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                LibraryViewModel(type, app.services.libraryRepository)
            }
        }
    }
}
