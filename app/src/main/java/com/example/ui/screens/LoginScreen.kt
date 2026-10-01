package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.QuickChatButton
import com.example.ui.components.QuickChatTextField
import com.example.ui.components.QuickChatTopBar
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.QuickChatPrimary
import com.example.ui.theme.StatusError
import com.example.ui.viewmodels.AuthUiState
import com.example.ui.viewmodels.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val authState by viewModel.uiState.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var clientError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            onLoginSuccess()
        }
    }

Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("login_screen")
        ) {
            QuickChatTopBar(
                title = "Sign In",
                subtitle = "Welcome Back to Quick Chat",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brand logo
                Image(
                    painter = painterResource(id = R.drawable.quick_chat_brand_icon),
                    contentDescription = "Quick Chat Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Username (Strictly Username, never Email)
            QuickChatTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }
                    clientError = null
                },
                label = "Username",
                placeholder = "e.g. jordan_hayes",
                leadingIcon = Icons.Default.Person,
                testTag = "login_username_input"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Password
            QuickChatTextField(
                value = password,
                onValueChange = {
                    password = it
                    clientError = null
                },
                label = "Password",
                placeholder = "••••••••",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                testTag = "login_password_input"
            )

            val displayError = clientError ?: (authState as? AuthUiState.Error)?.message
            if (displayError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = displayError,
                    color = StatusError,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            QuickChatButton(
                text = "Sign In",
                onClick = {
                    when {
                        username.isBlank() -> clientError = "Please enter your username"
                        password.isBlank() -> clientError = "Please enter your password"
                        else -> {
                            clientError = null
                            viewModel.login(username, password)
                        }
                    }
                },
                isLoading = authState is AuthUiState.Loading,
                testTag = "login_submit_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.testTag("switch_to_register_button")
            ) {
                Text(
                    text = "Don't have an account? Create one",
                    color = QuickChatPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
