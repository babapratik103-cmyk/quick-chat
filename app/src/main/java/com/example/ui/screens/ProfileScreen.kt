package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.QuickChatButton
import com.example.ui.components.QuickChatTextField
import com.example.ui.components.QuickChatTopBar
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.QuickChatPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodels.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            name = user.name
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("profile_screen")
    ) {
        QuickChatTopBar(
            title = "Profile",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            onNavigationClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Initial Avatar
            InitialsAvatar(
                name = currentUser?.username ?: "",
                colorHex = currentUser?.avatarColorHex ?: "#F59E0B",
                size = 88.dp,
                fontSize = 34
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Dominant Identity: @username
            Text(
                text = currentUser?.let { "@${it.username}" } ?: "",
                color = QuickChatPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Editable Name Section
            QuickChatTextField(
                value = name,
                onValueChange = { name = it },
                label = "Full Name",
                placeholder = "Your registered full name",
                leadingIcon = Icons.Default.Badge,
                testTag = "profile_name_input"
            )

            Spacer(modifier = Modifier.height(10.dp))

            QuickChatButton(
                text = "Save Name",
                onClick = {
                    val colorHex = currentUser?.avatarColorHex ?: "#F59E0B"
                    viewModel.updateProfile(name, colorHex)
                    Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                },
                isLoading = isSaving,
                enabled = name.isNotBlank(),
                testTag = "save_profile_button"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Read-only Account Information
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Account Information",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileFieldRow(
                        icon = Icons.Default.Person,
                        label = "Name",
                        value = currentUser?.name ?: ""
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ProfileFieldRow(
                        icon = Icons.Default.Cake,
                        label = "Age",
                        value = currentUser?.let { "${it.age}" } ?: ""
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ProfileFieldRow(
                        icon = Icons.Default.Email,
                        label = "Verification Email",
                        value = currentUser?.email ?: ""
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log Out Button
            QuickChatButton(
                text = "Log Out",
                onClick = { showLogoutDialog = true },
                isSecondary = true,
                leadingIcon = Icons.Default.Logout,
                testTag = "profile_logout_button"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Log Out",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of Quick Chat?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLoggedOut)
                    },
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Log Out", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    modifier = Modifier.testTag("cancel_logout_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ProfileFieldRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = QuickChatPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
