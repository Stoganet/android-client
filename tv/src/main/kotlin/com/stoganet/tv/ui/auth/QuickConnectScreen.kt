package com.stoganet.tv.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuickConnectScreen(
    state: QuickConnectUiState,
    onIntent: (QuickConnectIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state.status) {
            QuickConnectUiState.Status.Loading -> CircularProgressIndicator()

            QuickConnectUiState.Status.WaitingForApproval -> WaitingContent(state.code)

            QuickConnectUiState.Status.Expired -> RetryContent(
                message = stringResource(R.string.login_quick_connect_expired),
                onRetry = { onIntent(QuickConnectIntent.Retry) },
            )

            QuickConnectUiState.Status.Error -> RetryContent(
                message = stringResource(R.string.error_cant_reach_server),
                onRetry = { onIntent(QuickConnectIntent.Retry) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WaitingContent(code: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = stringResource(R.string.login_quick_connect_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = code, style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.login_quick_connect_instruction),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.login_quick_connect_waiting), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetryContent(message: String, onRetry: () -> Unit) {
    val retryLabel = stringResource(R.string.action_retry)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = message, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.semantics { contentDescription = retryLabel },
        ) {
            Text(text = retryLabel)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLoading() {
    QuickConnectScreen(state = QuickConnectUiState(), onIntent = {})
}

@Preview(showBackground = true)
@Composable
private fun PreviewWaiting() {
    QuickConnectScreen(
        state = QuickConnectUiState(code = "ABC123", status = QuickConnectUiState.Status.WaitingForApproval),
        onIntent = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewExpired() {
    QuickConnectScreen(
        state = QuickConnectUiState(status = QuickConnectUiState.Status.Expired),
        onIntent = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewError() {
    QuickConnectScreen(
        state = QuickConnectUiState(status = QuickConnectUiState.Status.Error),
        onIntent = {},
    )
}
