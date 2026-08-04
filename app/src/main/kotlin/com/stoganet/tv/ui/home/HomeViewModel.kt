package com.stoganet.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.tv.R
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.api.model.HomeSection
import com.stoganet.tv.api.model.LibraryItem
import com.stoganet.tv.data.home.HomeRepository
import com.stoganet.tv.ui.AppRoutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadHome()
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> loadHome()
        }
    }

    private fun loadHome() {
        _state.update { HomeUiState.Loading }
        viewModelScope.launch {
            repository.getHome()
                .onSuccess { response ->
                    val sections = response.sections.map { it.toUiState() }.toImmutableList()
                    _state.update { HomeUiState.Content(sections) }
                }
                .onFailure {
                    _state.update { HomeUiState.Error }
                }
        }
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
        posterUrl = poster,
        contentDescription = "$title ($year)",
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
