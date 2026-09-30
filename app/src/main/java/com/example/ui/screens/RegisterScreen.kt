package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onRegistrationSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val authState by viewModel.uiState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var clientError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            onRegistrationSuccess()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("register_screen")
    ) {
        QuickChatTopBar(
            title = "Create Account",
            subtitle = "Registration (Step 1 of 1)",
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
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Name
            QuickChatTextField(
                value = name,
                onValueChange = {
                    name = it
                    clientError = null
                },
                label = "Full Name",
                placeholder = "e.g. Jordan Hayes",
                leadingIcon = Icons.Default.Badge,
                testTag = "register_name_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Username (Primary Identity)
            QuickChatTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }
                    clientError = null
                },
                label = "Username (3-20 chars, lowercase & _)",
                placeholder = "e.g. jordan_hayes",
                leadingIcon = Icons.Default.Person,
                testTag = "register_username_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Age
            QuickChatTextField(
                value = ageText,
                onValueChange = {
                    ageText = it.filter { ch -> ch.isDigit() }.take(3)
                    clientError = null
                },
                label = "Age",
                placeholder = "e.g. 24",
                leadingIcon = Icons.Default.Numbers,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "register_age_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Email (For verification/recovery only)
            QuickChatTextField(
                value = email,
                onValueChange = {
                    email = it
                    clientError = null
                },
                label = "Email Address (Verification only)",
                placeholder = "jordan@example.com",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                testTag = "register_email_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Password
            QuickChatTextField(
                value = password,
                onValueChange = {
                    password = it
                    clientError = null
                },
                label = "Password",
                placeholder = "Minimum 6 characters",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                testTag = "register_password_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Confirm Password
            QuickChatTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    clientError = null
                },
                label = "Confirm Password",
                placeholder = "Re-enter password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                testTag = "register_confirm_password_input"
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

            Spacer(modifier = Modifier.height(26.dp))

            QuickChatButton(
                text = "Create Account",
                onClick = {
                    val ageInt = ageText.toIntOrNull()
                    when {
                        name.isBlank() -> clientError = "Please enter your name"
                        username.length < 3 -> clientError = "Username must be at least 3 characters"
                        ageInt == null || ageInt < 13 -> clientError = "Please enter a valid age (13 or older)"
                        !email.contains("@") || !email.contains(".") -> clientError = "Please enter a valid email address"
                        password.length < 6 -> clientError = "Password must be at least 6 characters"
                        password != confirmPassword -> clientError = "Passwords do not match"
                        else -> {
                            clientError = null
                            viewModel.register(
                                name = name,
                                username = username,
                                age = ageInt,
                                email = email,
                                pass = password,
                                confirmPass = confirmPassword
                            )
                        }
                    }
                },
                isLoading = authState is AuthUiState.Loading,
                testTag = "register_submit_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.testTag("switch_to_login_button")
            ) {
                Text(
                    text = "Already have an account? Sign In",
                    color = QuickChatPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
