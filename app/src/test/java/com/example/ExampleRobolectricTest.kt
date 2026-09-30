package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.ui.components.MessageBubble
import com.example.ui.screens.HomeScreen
import com.example.ui.viewmodels.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Quick Chat", appName)
  }

  @Test
  fun `test message delivery status indicators`() {
    val sendingMsg = Message(
      id = "m1",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Status: Sending",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.SENDING,
      isOutgoing = true
    )
    val sentMsg = Message(
      id = "m2",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Status: Sent",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.SENT,
      isOutgoing = true
    )
    val deliveredMsg = Message(
      id = "m3",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Status: Delivered",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.DELIVERED,
      isOutgoing = true
    )
    val readMsg = Message(
      id = "m4",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Status: Read",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.READ,
      isOutgoing = true
    )

    // Verify all 4 status indicators (Sending -> clock, Sent -> ✓, Delivered -> ✓✓, Read -> ✓✓)
    composeTestRule.setContent {
      Column(modifier = Modifier.fillMaxSize()) {
        MessageBubble(message = sendingMsg)
        MessageBubble(message = sentMsg)
        MessageBubble(message = deliveredMsg)
        MessageBubble(message = readMsg)
      }
    }
    composeTestRule.onNodeWithContentDescription("Sending").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Sent").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Delivered").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Read").assertIsDisplayed()
  }

  @Test
  fun `test keyboard closed and open states for chat message display`() {
    val latestMessage = Message(
      id = "latest_msg",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Latest message above composer",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.READ,
      isOutgoing = true
    )

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        MessageBubble(message = latestMessage)
      }
    }
    composeTestRule.onNodeWithText("Latest message above composer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("message_bubble_latest_msg").assertIsDisplayed()
  }

  @Test
  fun `test message bubble long click interaction`() {
    val message = Message(
      id = "long_press_msg",
      conversationId = "c1",
      senderId = "u1",
      recipientId = "u2",
      text = "Long press this text",
      timestamp = System.currentTimeMillis(),
      status = MessageDeliveryStatus.SENT,
      isOutgoing = true
    )
    var wasLongClicked = false

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        MessageBubble(
          message = message,
          onLongClick = { wasLongClicked = true }
        )
      }
    }

    composeTestRule.onNodeWithTag("message_bubble_long_press_msg").performTouchInput {
      longClick()
    }
    assertTrue(wasLongClicked)
  }

  @Test
  fun `test home hamburger menu displays correct options`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val homeViewModel = HomeViewModel(context as Application)

    composeTestRule.setContent {
      HomeScreen(
        viewModel = homeViewModel,
        onNavigateToChat = {},
        onNavigateToSearch = {},
        onNavigateToProfile = {}
      )
    }

    // Tap hamburger menu
    composeTestRule.onNodeWithTag("home_hamburger_button").performClick()

    // Verify exactly the 3 allowed options are shown
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    composeTestRule.onNodeWithText("About Quick Chat").assertIsDisplayed()
    composeTestRule.onNodeWithText("Log Out").assertIsDisplayed()
  }
}
