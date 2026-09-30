package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = DefaultChatRepository(application)
) : AndroidViewModel(application) {

    val currentUser: StateFlow<User?> = repository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoggedOut = MutableStateFlow(false)
    val isLoggedOut: StateFlow<Boolean> = _isLoggedOut.asStateFlow()

    fun updateProfile(name: String, avatarColorHex: String) {
        viewModelScope.launch {
            _isSaving.value = true
            repository.updateProfile(name, avatarColorHex)
            _isSaving.value = false
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _isLoggedOut.value = true
            onLoggedOut()
        }
    }
}
