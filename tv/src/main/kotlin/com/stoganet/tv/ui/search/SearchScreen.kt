package com.stoganet.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R
import com.stoganet.tv.ui.home.PosterCard
import kotlinx.collections.immutable.persistentListOf

private const val GRID_COLUMNS = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by rememberSaveable { mutableStateOf(state.query) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        val hint = stringResource(R.string.search_hint)
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onIntent(SearchIntent.QueryChanged(it))
            },
            label = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onIntent(SearchIntent.Submit) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = hint },
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            is SearchUiState.Empty -> CenteredMessage(stringResource(R.string.search_empty_hint))

            is SearchUiState.Loading -> CenteredLoading()

            is SearchUiState.Results -> SearchResultsGrid(state = state, onNavigateToDetail = onNavigateToDetail)

            is SearchUiState.NoResults -> CenteredMessage(
                stringResource(R.string.search_no_results, state.query),
            )

            is SearchUiState.Error -> SearchError(onIntent = onIntent)
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchError(onIntent: (SearchIntent) -> Unit) {
    val retryLabel = stringResource(R.string.action_retry)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = stringResource(R.string.search_error_message), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onIntent(SearchIntent.Submit) },
                modifier = Modifier.semantics { contentDescription = retryLabel },
            ) { Text(retryLabel) }
        }
    }
}

@Composable
private fun SearchResultsGrid(state: SearchUiState.Results, onNavigateToDetail: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.id }) { item ->
            PosterCard(
                posterUrl = item.posterUrl,
                contentDescription = item.contentDescription,
                onClick = { onNavigateToDetail(item.id) },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewEmpty() {
    SearchScreen(state = SearchUiState.Empty(), onIntent = {}, onNavigateToDetail = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewLoading() {
    SearchScreen(state = SearchUiState.Loading("ne"), onIntent = {}, onNavigateToDetail = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewResults() {
    val items = persistentListOf(
        SearchResultUiState("1", "", "Movie One (2020)"),
        SearchResultUiState("2", "", "Movie Two (2021)"),
        SearchResultUiState("3", "", "Movie Three (2022)"),
    )
    SearchScreen(state = SearchUiState.Results("movie", items), onIntent = {}, onNavigateToDetail = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewNoResults() {
    SearchScreen(state = SearchUiState.NoResults("zzz"), onIntent = {}, onNavigateToDetail = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewError() {
    SearchScreen(state = SearchUiState.Error("movie"), onIntent = {}, onNavigateToDetail = {})
}
