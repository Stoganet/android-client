package com.stoganet.tv.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onIntent: (LoginIntent) -> Unit,
    onSwitchToQuickConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            val usernameLabel = stringResource(R.string.login_username_hint)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(usernameLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .width(320.dp)
                    .semantics { contentDescription = usernameLabel },
            )
            Spacer(modifier = Modifier.height(8.dp))

            val passwordLabel = stringResource(R.string.login_password_hint)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(passwordLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .width(320.dp)
                    .semantics { contentDescription = passwordLabel },
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (state.status) {
                LoginUiState.Status.Loading -> CircularProgressIndicator()
                LoginUiState.Status.CredentialsError -> ErrorText(stringResource(R.string.login_error_credentials))
                LoginUiState.Status.NetworkError -> ErrorText(stringResource(R.string.error_cant_reach_server))
                LoginUiState.Status.Idle -> Unit
            }
            Spacer(modifier = Modifier.height(16.dp))

            val submitLabel = stringResource(R.string.login_submit)
            Button(
                onClick = { onIntent(LoginIntent.Submit(username = username, password = password)) },
                modifier = Modifier.semantics { contentDescription = submitLabel },
            ) {
                Text(text = submitLabel)
            }
            Spacer(modifier = Modifier.height(8.dp))

            val quickConnectLabel = stringResource(R.string.login_switch_to_quick_connect)
            Button(
                onClick = onSwitchToQuickConnect,
                modifier = Modifier.semantics { contentDescription = quickConnectLabel },
            ) {
                Text(text = quickConnectLabel)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorText(message: String) {
    Text(text = message, style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true)
@Composable
private fun PreviewIdle() {
    LoginScreen(state = LoginUiState(), onIntent = {}, onSwitchToQuickConnect = {})
}

@Preview(showBackground = true)
@Composable
private fun PreviewLoading() {
    LoginScreen(
        state = LoginUiState(status = LoginUiState.Status.Loading),
        onIntent = {},
        onSwitchToQuickConnect = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialsError() {
    LoginScreen(
        state = LoginUiState(status = LoginUiState.Status.CredentialsError),
        onIntent = {},
        onSwitchToQuickConnect = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewNetworkError() {
    LoginScreen(
        state = LoginUiState(status = LoginUiState.Status.NetworkError),
        onIntent = {},
        onSwitchToQuickConnect = {},
    )
}
