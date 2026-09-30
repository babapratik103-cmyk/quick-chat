package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SupabaseCredentials(
    val url: String,
    val anonKey: String
)

class SupabaseConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_chat_supabase_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SUPABASE_URL = "https://your-project.supabase.co"
        const val DEFAULT_SUPABASE_ANON_KEY = "public-anon-key-placeholder"
    }

    private val _credentials = MutableStateFlow(loadCredentials())
    val credentials: StateFlow<SupabaseCredentials> = _credentials.asStateFlow()

    private fun loadCredentials(): SupabaseCredentials {
        val url = prefs.getString("supabase_url", DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
        val anonKey = prefs.getString("supabase_anon_key", DEFAULT_SUPABASE_ANON_KEY) ?: DEFAULT_SUPABASE_ANON_KEY
        return SupabaseCredentials(url = url.trim(), anonKey = anonKey.trim())
    }

    fun updateCredentials(url: String, anonKey: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanKey = anonKey.trim()
        prefs.edit()
            .putString("supabase_url", cleanUrl)
            .putString("supabase_anon_key", cleanKey)
            .apply()
        _credentials.value = SupabaseCredentials(url = cleanUrl, anonKey = cleanKey)
    }

    val currentUrl: String
        get() = _credentials.value.url

    val currentAnonKey: String
        get() = _credentials.value.anonKey

    val isCustomConfigured: Boolean
        get() = currentUrl.isNotEmpty() &&
                !currentUrl.contains("your-project.supabase.co") &&
                currentAnonKey.isNotEmpty() &&
                !currentAnonKey.contains("placeholder")
}
