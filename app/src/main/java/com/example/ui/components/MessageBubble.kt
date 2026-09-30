package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.ui.theme.BubbleBorderRecipient
import com.example.ui.theme.BubbleBorderSender
import com.example.ui.theme.BubbleRecipient
import com.example.ui.theme.BubbleSender
import com.example.ui.theme.QuickChatPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (isOutgoing) BubbleSender else BubbleRecipient
    val borderColor = if (isOutgoing) BubbleBorderSender else BubbleBorderRecipient

    val timeString = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 72.dp, max = 290.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("message_bubble_${message.id}")
        ) {
            Column {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = timeString,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageDeliveryStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Sending",
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageDeliveryStatus.SENT -> {
                                // Sent ✓ (single checkmark in secondary gray)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageDeliveryStatus.DELIVERED -> {
                                // Delivered ✓✓ (double checkmarks in secondary gray)
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageDeliveryStatus.READ -> {
                                // Read ✓✓ (double checkmarks in primary amber accent)
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = QuickChatPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
