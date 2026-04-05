package com.example.rxaide.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.rxaide.ui.screens.AddMedicationScreen
import com.example.rxaide.ui.screens.AdherenceScreen
import com.example.rxaide.ui.screens.CameraScreen
import com.example.rxaide.ui.screens.ChatScreen
import com.example.rxaide.ui.screens.EditMedicationScreen
import com.example.rxaide.ui.screens.HomeScreen
import com.example.rxaide.ui.screens.MedicationDetailScreen
import com.example.rxaide.ui.screens.MedicationListScreen
import com.example.rxaide.ui.screens.NotificationsScreen
import com.example.rxaide.ui.theme.MedicalBlue
import com.example.rxaide.viewmodel.AdherenceViewModel
import com.example.rxaide.viewmodel.ChatViewModel
import com.example.rxaide.viewmodel.MedicationViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.abs

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val index: Int
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home, 0),
    BottomNavItem("Meds", Screen.MedicationList.route, Icons.Filled.Medication, Icons.Outlined.Medication, 1),
    BottomNavItem("Chat", Screen.Chat.route, Icons.Filled.SmartToy, Icons.Outlined.SmartToy, 2),
    BottomNavItem("Alerts", Screen.Notifications.route, Icons.Filled.Notifications, Icons.Outlined.Notifications, 3),
    BottomNavItem("Tracker", Screen.AdherenceTracker.route, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, 4)
)

// All routes that should show the bottom bar
private val bottomBarRoutes = bottomNavItems.map { it.route }.toSet()

private fun getNavItemIndex(route: String?): Int {
    return bottomNavItems.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RxAideNavGraph(
    navController: NavHostController,
    viewModel: MedicationViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    adherenceViewModel: AdherenceViewModel = viewModel(),
    isDarkMode: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes
    val todayPendingCount by viewModel.todayUnmarkedCount.collectAsState()

    // Track the previous nav item index for directional slide transitions
    var previousIndex by rememberSaveable { mutableIntStateOf(0) }

    var isRefreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.pointerInput(currentRoute) {
            if (showBottomBar) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        if (abs(dragX) > 100f) {
                            val currentIndex = getNavItemIndex(currentRoute)
                            if (dragX < 0 && currentIndex < bottomNavItems.size - 1) { 
                                // swipe left -> next screen
                                val nextRoute = bottomNavItems[currentIndex + 1].route
                                previousIndex = currentIndex
                                navController.navigate(nextRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else if (dragX > 0 && currentIndex > 0) { 
                                // swipe right -> previous screen
                                val prevRoute = bottomNavItems[currentIndex - 1].route
                                previousIndex = currentIndex
                                navController.navigate(prevRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        dragX += dragAmount
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    previousIndex = getNavItemIndex(currentRoute)
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (item.route == Screen.Notifications.route && todayPendingCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = Color(0xFFEF4444),
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    if (todayPendingCount > 99) "99+" else todayPendingCount.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (isSelected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MedicalBlue,
                                selectedTextColor = MedicalBlue,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    com.example.rxaide.notification.DoseGenerationWorker.enqueue(context)
                    delay(1500)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            val transitionDuration = 250

            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = {
                    val fromIndex = getNavItemIndex(initialState.destination.route)
                    val toIndex = getNavItemIndex(targetState.destination.route)
                    if (targetState.destination.route in bottomBarRoutes && initialState.destination.route in bottomBarRoutes) {
                        if (toIndex > fromIndex) {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(transitionDuration))
                        } else {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(transitionDuration))
                        }
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(transitionDuration))
                    }
                },
                exitTransition = {
                    val fromIndex = getNavItemIndex(initialState.destination.route)
                    val toIndex = getNavItemIndex(targetState.destination.route)
                    if (targetState.destination.route in bottomBarRoutes && initialState.destination.route in bottomBarRoutes) {
                        if (toIndex > fromIndex) {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(transitionDuration))
                        } else {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(transitionDuration))
                        }
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(transitionDuration))
                    }
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(transitionDuration))
                },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(transitionDuration))
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToMedications = {
                        navController.navigate(Screen.MedicationList.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAddMedication = {
                        navController.navigate(Screen.AddMedication.route)
                    },
                    onNavigateToCamera = {
                        navController.navigate(Screen.Camera.route)
                    },
                    onNavigateToChat = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAdherence = {
                        navController.navigate(Screen.AdherenceTracker.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                com.example.rxaide.ui.screens.SettingsScreen(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onNavigateBack = { navController.popBackStack() }
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
                val capturedImagePath by viewModel.capturedImagePath.collectAsState()

                CameraScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )

                LaunchedEffect(capturedImagePath) {
                    capturedImagePath?.let { uri ->
                        chatViewModel.sendImageMessage(uri)
                        viewModel.setCapturedImagePath(null)
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
                        navController.navigate(Screen.MedicationList.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToCamera = {
                        navController.navigate(Screen.Camera.route)
                    }
                )
            }

            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    viewModel = adherenceViewModel,
                    onNavigateToAdherence = {
                        navController.navigate(Screen.AdherenceTracker.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.AdherenceTracker.route) {
                AdherenceScreen(
                    viewModel = adherenceViewModel,
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
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(Screen.EditMedication.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.EditMedication.route,
                arguments = listOf(
                    navArgument("medicationId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: return@composable
                EditMedicationScreen(
                    viewModel = viewModel,
                    medicationId = medicationId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}
