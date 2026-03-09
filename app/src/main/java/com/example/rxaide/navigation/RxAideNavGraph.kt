package com.example.rxaide.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.rxaide.ui.screens.AddMedicationScreen
import com.example.rxaide.ui.screens.CameraScreen
import com.example.rxaide.ui.screens.ChatScreen
import com.example.rxaide.ui.screens.HomeScreen
import com.example.rxaide.ui.screens.MedicationDetailScreen
import com.example.rxaide.ui.screens.MedicationListScreen
import com.example.rxaide.viewmodel.ChatViewModel
import com.example.rxaide.viewmodel.MedicationViewModel

@Composable
fun RxAideNavGraph(
    navController: NavHostController,
    viewModel: MedicationViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToMedications = {
                    navController.navigate(Screen.MedicationList.route)
                },
                onNavigateToAddMedication = {
                    navController.navigate(Screen.AddMedication.route)
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                },
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.route)
                }
            )
        }

        composable(Screen.MedicationList.route) {
            MedicationListScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddMedication = {
                    navController.navigate(Screen.AddMedication.route)
                },
                onNavigateToDetail = { medicationId ->
                    navController.navigate(Screen.MedicationDetail.createRoute(medicationId))
                }
            )
        }

        composable(Screen.AddMedication.route) {
            AddMedicationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        composable(Screen.Camera.route) {
            // After image capture, navigate to Chat with the image
            val capturedImagePath by viewModel.capturedImagePath.collectAsState()

            CameraScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )

            // When an image is captured, send it to chat and navigate there
            LaunchedEffect(capturedImagePath) {
                capturedImagePath?.let { uri ->
                    chatViewModel.sendImageMessage(uri)
                    viewModel.setCapturedImagePath(null) // Clear after sending
                    // Navigate to chat, replacing camera in the back stack
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                }
            }
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMedications = {
                    navController.navigate(Screen.MedicationList.route)
                }
            )
        }

        composable(
            route = Screen.MedicationDetail.route,
            arguments = listOf(
                navArgument("medicationId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: return@composable
            MedicationDetailScreen(
                viewModel = viewModel,
                medicationId = medicationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
