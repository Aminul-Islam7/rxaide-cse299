package com.example.rxaide.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object MedicationList : Screen("medication_list")
    data object AddMedication : Screen("add_medication")
    data object Camera : Screen("camera")
    data object MedicationDetail : Screen("medication_detail/{medicationId}") {
        fun createRoute(medicationId: Long) = "medication_detail/$medicationId"
    }
}
