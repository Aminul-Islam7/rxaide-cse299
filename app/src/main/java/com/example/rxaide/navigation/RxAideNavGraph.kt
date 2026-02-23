package com.example.rxaide.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.rxaide.ui.screens.AddMedicationScreen
import com.example.rxaide.ui.screens.CameraScreen
import com.example.rxaide.ui.screens.HomeScreen
import com.example.rxaide.ui.screens.MedicationDetailScreen
import com.example.rxaide.ui.screens.MedicationListScreen
import com.example.rxaide.viewmodel.MedicationViewModel

@Composable
fun RxAideNavGraph(
    navController: NavHostController,
    viewModel: MedicationViewModel = viewModel()
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
            CameraScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
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
