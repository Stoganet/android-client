package com.stoganet.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.AppRoutes
import com.stoganet.core.api.model.HomeSection
import com.stoganet.core.api.model.LibraryItem
import com.stoganet.core.data.home.HomeRepository
import com.stoganet.tv.R
import com.stoganet.tv.StoganetApp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val focusedItemKey = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        retries.onStart { emit(Unit) }.flatMapLatest { load() },
        focusedItemKey,
    ) { home, focusedKey ->
        if (home is HomeUiState.Content) home.copy(focusedItemKey = focusedKey) else home
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> {
                focusedItemKey.value = null
                retries.tryEmit(Unit)
            }

            is HomeIntent.ItemClicked -> focusedItemKey.value = intent.key
        }
    }

    private fun load(): Flow<HomeUiState> = flow {
        emit(HomeUiState.Loading)
        emit(
            repository.getHome().fold(
                onSuccess = { response ->
                    HomeUiState.Content(
                        response.sections.map { it.toUiState() }.toImmutableList(),
                    )
                },
                onFailure = { HomeUiState.Error },
            ),
        )
    }

    private fun HomeSection.toUiState() = HomeSectionUiState(
        id = id,
        titleRes = sectionTitleRes(id),
        items = items.map { it.toUiState() }.toImmutableList(),
        hasMore = hasMore,
        seeMoreRoute = sectionSeeMoreRoute(id),
    )

    private fun LibraryItem.toUiState() = HomeItemUiState(
        id = id,
        title = title,
        year = year,
        overview = overview,
        contentDescription = if (year > 0) "$title ($year)" else title,
        backdropUrl = backdrop,
        thumbUrl = thumb,
        thumbHasLogo = thumbHasLogo,
    )

    companion object {
        private fun sectionTitleRes(id: String) = when (id) {
            "recently_added_movies" -> R.string.home_section_recently_added_movies
            "recently_added_tv" -> R.string.home_section_recently_added_tv
            "all_movies" -> R.string.home_section_all_movies
            "all_tv" -> R.string.home_section_all_tv
            else -> R.string.home_section_unknown
        }

        private fun sectionSeeMoreRoute(id: String) = when (id) {
            "all_movies" -> AppRoutes.LIBRARY_MOVIES
            "all_tv" -> AppRoutes.LIBRARY_TV
            else -> null
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                HomeViewModel(app.services.homeRepository)
            }
        }
    }
}
