package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSearchViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = DefaultChatRepository(application)
) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Initial search to display all other discoverable users
        search("")
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200) // Debounce
            search(newQuery)
        }
    }

    private fun search(q: String) {
        viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchUsers(q)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    suspend fun startConversationWithUser(user: User) {
        repository.startConversationWithUser(user)
    }
}
