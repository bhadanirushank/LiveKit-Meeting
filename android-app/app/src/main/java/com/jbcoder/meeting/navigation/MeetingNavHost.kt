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
                onSuccess = { navController.navigate("waitingRoomRouter") { popUpTo("home") } }
            )
        }
        composable("joinMeeting") {
            JoinMeetingScreen(
                onNavigateBack = { navController.popBackStack() },
                onSuccess = { navController.navigate("waitingRoomRouter") { popUpTo("home") } }
            )
        }
        composable("waitingRoomRouter") {
            com.jbcoder.meeting.feature.waitingroom.WaitingRoomRouterScreen(
                onNavigateToHost = {
                    navController.navigate("hostWaitingRoom") {
                        popUpTo("home")
                    }
                },
                onNavigateToParticipant = {
                    navController.navigate("participantWaitingRoom") {
                        popUpTo("home")
                    }
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("hostWaitingRoom") {
            com.jbcoder.meeting.feature.waitingroom.HostWaitingRoomScreen(
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateLiveRoom = {
                    navController.navigate("roomPreJoin") {
                        popUpTo("home")
                    }
                }
            )
        }
        composable("participantWaitingRoom") {
            com.jbcoder.meeting.feature.waitingroom.ParticipantWaitingRoomScreen(
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateLiveRoom = {
                    navController.navigate("roomPreJoin") {
                        popUpTo("home")
                    }
                }
            )
        }
        composable("roomPreJoin") {
            com.jbcoder.meeting.feature.room.RoomPreJoinScreen(
                onNavigateLiveRoom = {
                    navController.navigate("liveRoom") {
                        popUpTo("roomPreJoin") { inclusive = true }
                    }
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("liveRoom") {
            com.jbcoder.meeting.feature.room.LiveRoomScreen(
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
