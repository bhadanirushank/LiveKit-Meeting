package com.jbcoder.meeting.feature.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jbcoder.meeting.presentation.theme.MeetingTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_readyState_displaysReadyStatusAndEnabledButtons() {
        var createMeetingClicked = false
        var joinMeetingClicked = false

        composeTestRule.setContent {
            MeetingTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Ready,
                    onNavigateToCreate = { createMeetingClicked = true },
                    onNavigateToJoin = { joinMeetingClicked = true },
                    onRetry = {}
                )
            }
        }

        // Verify Status
        composeTestRule.onNodeWithContentDescription("Status: Ready to connect").assertIsDisplayed()

        // Verify Headline
        composeTestRule.onNodeWithText("Meet clearly.\nConnect simply.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start a meeting or join securely with a meeting code.").assertIsDisplayed()

        // Verify Buttons
        composeTestRule.onNodeWithText("Create Meeting").assertIsEnabled().performClick()
        assert(createMeetingClicked)

        composeTestRule.onNodeWithText("Join with Code").assertIsEnabled().performClick()
        assert(joinMeetingClicked)
    }

    @Test
    fun homeScreen_initializingState_displaysInitializingStatusAndDisabledButtons() {
        composeTestRule.setContent {
            MeetingTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Initializing,
                    onNavigateToCreate = { },
                    onNavigateToJoin = { },
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Status: Initializing...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Meeting").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Join with Code").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Retry Connection").assertDoesNotExist()
    }

    @Test
    fun homeScreen_offlineState_displaysOfflineStatusAndRetryButton() {
        var retryClicked = false

        composeTestRule.setContent {
            MeetingTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Offline(),
                    onNavigateToCreate = { },
                    onNavigateToJoin = { },
                    onRetry = { retryClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Status: Backend is offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Meeting").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Join with Code").assertIsNotEnabled()
        
        composeTestRule.onNodeWithText("Retry Connection").assertIsDisplayed().performClick()
        assert(retryClicked)
    }
}
