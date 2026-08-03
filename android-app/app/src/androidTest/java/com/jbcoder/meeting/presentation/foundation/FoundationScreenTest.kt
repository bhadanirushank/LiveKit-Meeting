package com.jbcoder.meeting.presentation.foundation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoadingStateRenders() {
        composeTestRule.setContent {
            FoundationScreenContent(
                state = FoundationState.Loading,
                onReady = {},
                onRetry = {}
            )
        }
        
        composeTestRule.onNodeWithText("Initializing Foundation...").assertExists()
    }

    @Test
    fun testReadyStateNavigates() {
        var navigated = false
        composeTestRule.setContent {
            FoundationScreenContent(
                state = FoundationState.Ready,
                onReady = { navigated = true },
                onRetry = {}
            )
        }
        
        assertTrue(navigated)
    }

    @Test
    fun testErrorStateRendersAndRetryIsCallable() {
        var retryCalled = false
        composeTestRule.setContent {
            FoundationScreenContent(
                state = FoundationState.Error("Simulated failure"),
                onReady = {},
                onRetry = { retryCalled = true }
            )
        }
        
        composeTestRule.onNodeWithText("Initialization Failed: Simulated failure").assertExists()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retryCalled)
    }

    @Test
    fun testOfflineStateRendersAndRetryIsCallable() {
        var retryCalled = false
        composeTestRule.setContent {
            FoundationScreenContent(
                state = FoundationState.Offline(),
                onReady = {},
                onRetry = { retryCalled = true }
            )
        }
        
        composeTestRule.onNodeWithText("Backend is offline or unreachable").assertExists()
        composeTestRule.onNodeWithText("Retry Connection").performClick()
        assertTrue(retryCalled)
    }
}
