package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Message
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.MessageBubble
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.QuickChatPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodels.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    peerId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    LaunchedEffect(peerId) {
        viewModel.initChat(peerId)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Local Chat Screen State for frontend-phase Clear and Delete
    var isChatCleared by remember { mutableStateOf(false) }
    var localDeletedMessageIds by remember { mutableStateOf(setOf<String>()) }

    // Filter displayed messages locally
    val displayedMessages = remember(messages, isChatCleared, localDeletedMessageIds) {
        if (isChatCleared) {
            emptyList()
        } else {
            messages.filter { it.id !in localDeletedMessageIds }
        }
    }

    // Menu and Dialog states
    var showChatMenu by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var selectedMessageForAction by remember { mutableStateOf<Message?>(null) }
    var showMessageActionsDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Ensure latest messages remain visible when new messages arrive or keyboard opens
    LaunchedEffect(displayedMessages.size, imeBottom) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("chat_screen")
    ) {
        // Chat Header Top Bar with Username-First Identity and ⋮ Menu
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(
                        name = conversation?.peerUser?.username ?: "Peer",
                        colorHex = conversation?.peerUser?.avatarColorHex ?: "#F59E0B",
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = conversation?.let { "@${it.peerUser.username}" } ?: "Chat",
                            color = QuickChatPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = conversation?.peerUser?.name ?: "1-to-1 Conversation",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            actions = {
                Box {
                    IconButton(
                        onClick = { showChatMenu = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("chat_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Chat Options",
                            tint = TextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showChatMenu,
                        onDismissRequest = { showChatMenu = false },
                        modifier = Modifier
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .testTag("chat_dropdown_menu")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear Chat", color = StatusError, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showChatMenu = false
                                showClearChatDialog = true
                            },
                            modifier = Modifier.testTag("menu_item_clear_chat")
                        )

                        DropdownMenuItem(
                            text = { Text("Chat Theme", color = TextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = QuickChatPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showChatMenu = false
                                showThemeDialog = true
                            },
                            modifier = Modifier.testTag("menu_item_chat_theme")
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground,
                titleContentColor = TextPrimary
            ),
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier.border(width = 1.dp, color = DarkBorder)
        )

        // Scrollable Message Timeline - naturally resizes with keyboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (displayedMessages.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (isChatCleared) "Chat cleared." else "Say hello to start the conversation!",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayedMessages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onLongClick = {
                                selectedMessageForAction = msg
                                showMessageActionsDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Chat Composer - sits directly above keyboard or navigation bar without jumping
        Surface(
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = DarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Type a message...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant, // #242424 input background
                        unfocusedContainerColor = DarkSurfaceVariant, // #242424 input background
                        cursorColor = QuickChatPrimary,
                        focusedIndicatorColor = QuickChatPrimary,
                        unfocusedIndicatorColor = DarkBorder
                    ),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = inputText.isNotBlank() && !isSending

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) QuickChatPrimary else DarkBorder)
                        .clickable(enabled = canSend) {
                            val textToSend = inputText
                            inputText = ""
                            // If chat was cleared locally, reset clear state so new messages show
                            if (isChatCleared) {
                                isChatCleared = false
                            }
                            viewModel.sendMessage(textToSend)
                        }
                        .testTag("chat_send_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) DarkBackground else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // 1. Clear Chat Confirmation Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Clear Chat",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear this chat? Messages will be cleared from your display.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatDialog = false
                        isChatCleared = true
                        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_clear_chat_button")
                ) {
                    Text("Clear", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearChatDialog = false },
                    modifier = Modifier.testTag("cancel_clear_chat_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 2. Chat Theme Dialog (Coming Soon / UI Entry)
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Chat Theme",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Customize chat appearance (Coming Soon):",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    ThemeOptionRow(
                        title = "Default",
                        subtitle = "Dark #121212 minimal theme",
                        isSelected = true
                    )
                    ThemeOptionRow(
                        title = "Solid color",
                        subtitle = "Custom monochrome and accent colors",
                        isSelected = false
                    )
                    ThemeOptionRow(
                        title = "Local device photo",
                        subtitle = "Photo background from device storage",
                        isSelected = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false },
                    modifier = Modifier.testTag("close_theme_dialog_button")
                ) {
                    Text("Done", color = QuickChatPrimary)
                }
            }
        )
    }

    // 3. Message Long-Press Actions Dialog (Copy / Delete)
    if (showMessageActionsDialog && selectedMessageForAction != null) {
        val targetMsg = selectedMessageForAction!!
        AlertDialog(
            onDismissRequest = {
                showMessageActionsDialog = false
                selectedMessageForAction = null
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Message Actions",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Copy text action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(targetMsg.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                showMessageActionsDialog = false
                                selectedMessageForAction = null
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("action_copy_message"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = QuickChatPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Copy",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Delete message action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                messageToDelete = targetMsg
                                showMessageActionsDialog = false
                                showDeleteConfirmDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("action_delete_message"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete",
                            color = StatusError,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMessageActionsDialog = false
                        selectedMessageForAction = null
                    },
                    modifier = Modifier.testTag("cancel_message_actions_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 4. Delete Confirmation Dialog
    if (showDeleteConfirmDialog && messageToDelete != null) {
        val msgToDelete = messageToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                messageToDelete = null
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Delete Message",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this message from your display?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        localDeletedMessageIds = localDeletedMessageIds + msgToDelete.id
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                        selectedMessageForAction = null
                        Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_delete_message_button")
                ) {
                    Text("Delete", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                    },
                    modifier = Modifier.testTag("cancel_delete_message_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(
                width = 1.dp,
                color = if (isSelected) QuickChatPrimary else DarkBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) QuickChatPrimary else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = QuickChatPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
