package com.jbcoder.meeting.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jbcoder.meeting.presentation.foundation.FoundationScreen

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
            // Placeholder Home
            androidx.compose.material3.Text("Home Placeholder")
        }
    }
}
