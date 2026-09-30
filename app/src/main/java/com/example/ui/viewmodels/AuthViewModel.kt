package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = DefaultChatRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentSession()
    }

    fun checkCurrentSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val currentUser = repository.currentUserFlow.firstOrNull()
            if (currentUser != null) {
                _uiState.value = AuthUiState.Authenticated(currentUser)
            } else {
                _uiState.value = AuthUiState.Idle
            }
        }
    }

    // Login with Username and Password only
    fun login(username: String, pass: String) {
        val cleanUser = username.trim().removePrefix("@")
        if (cleanUser.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your username")
            return
        }
        if (pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your password")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(cleanUser, pass)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Authenticated(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Login failed")
                }
            )
        }
    }

    // Registration with exact 6 fields
    fun register(
        name: String,
        username: String,
        age: Int,
        email: String,
        pass: String,
        confirmPass: String
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(
                name = name,
                username = username,
                age = age,
                email = email,
                password = pass,
                confirmPassword = confirmPass
            )
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Authenticated(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Registration failed")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
