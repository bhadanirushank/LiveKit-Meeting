package com.jbcoder.meeting.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jbcoder.meeting.presentation.foundation.FoundationScreen
import com.jbcoder.meeting.feature.createmeeting.CreateMeetingScreen
import com.jbcoder.meeting.feature.home.HomeScreen
import com.jbcoder.meeting.feature.joinmeeting.JoinMeetingScreen
import com.jbcoder.meeting.feature.phase6chandoff.Phase6CHandoffPlaceholder

@Composable
fun MeetingNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "foundation") {
        composable("foundation") {
            FoundationScreen(
                onReady = {
                    navController.navigate("home") {
                        popUpTo("foundation") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToCreate = { navController.navigate("createMeeting") },
                onNavigateToJoin = { navController.navigate("joinMeeting") }
            )
        }
        composable("createMeeting") {
            CreateMeetingScreen(
                onNavigateBack = { navController.popBackStack() },
                onSuccess = { navController.navigate("phase6cHandoff") { popUpTo("home") } }
            )
        }
        composable("joinMeeting") {
            JoinMeetingScreen(
                onNavigateBack = { navController.popBackStack() },
                onSuccess = { navController.navigate("phase6cHandoff") { popUpTo("home") } }
            )
        }
        composable("phase6cHandoff") {
            Phase6CHandoffPlaceholder(
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
