package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UserSearchScreen
import com.example.ui.viewmodels.AuthUiState
import com.example.ui.viewmodels.AuthViewModel
import com.example.ui.viewmodels.ChatViewModel
import com.example.ui.viewmodels.HomeViewModel
import com.example.ui.viewmodels.ProfileViewModel
import com.example.ui.viewmodels.UserSearchViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Register : Screen("register")
    object Login : Screen("login")
    object Home : Screen("home")
    object UserSearch : Screen("user_search")
    object Profile : Screen("profile")
    object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String): String = "chat/$peerId"
    }
}

@Composable
fun QuickChatNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()

            SplashScreen(
                isLoggedIn = authState is AuthUiState.Authenticated,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            val authViewModel: AuthViewModel = viewModel()
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRegistrationSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = viewModel()
            LoginScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToChat = { peerId ->
                    navController.navigate(Screen.Chat.createRoute(peerId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.UserSearch.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.UserSearch.route) {
            val searchViewModel: UserSearchViewModel = viewModel()
            UserSearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onUserSelected = { peerId ->
                    navController.navigate(Screen.Chat.createRoute(peerId)) {
                        popUpTo(Screen.UserSearch.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val chatViewModel: ChatViewModel = viewModel()
            ChatScreen(
                viewModel = chatViewModel,
                peerId = peerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            val profileViewModel: ProfileViewModel = viewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
