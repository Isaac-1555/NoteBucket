package com.example.notebucket.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notebucket.ui.screens.FolderDetailScreen
import com.example.notebucket.ui.screens.HomeScreen
import com.example.notebucket.ui.screens.NoteDetailScreen
import com.example.notebucket.ui.screens.NoteInputScreen
import com.example.notebucket.ui.screens.OnboardingScreen
import com.example.notebucket.ui.screens.SearchScreen
import com.example.notebucket.ui.screens.SettingsScreen
import com.example.notebucket.ui.theme.NoteBucketTheme

@Composable
fun NoteBucketNavGraph() {
    val rootVm: RootViewModel = hiltViewModel()
    val onboardingDone by rootVm.onboardingDone.collectAsState()
    val themeMode by rootVm.themeMode.collectAsState()

    NoteBucketTheme(themeMode = themeMode) {
        when (onboardingDone) {
            null -> LoadingBox()
            else -> {
                val navController = rememberNavController()
                val startDest = if (onboardingDone!!) Routes.HOME else Routes.ONBOARDING
                NavHost(
                    navController = navController,
                    startDestination = startDest
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(navController)
                    }
                    composable(Routes.NOTE_INPUT) {
                        NoteInputScreen(navController)
                    }
                    composable(Routes.HOME) {
                        HomeScreen(navController)
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(navController)
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(navController)
                    }
                    composable(
                        route = Routes.FOLDER_DETAIL,
                        arguments = listOf(navArgument(Routes.FOLDER_DETAIL_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val folderId = entry.arguments?.getString(Routes.FOLDER_DETAIL_ARG).orEmpty()
                        FolderDetailScreen(navController, folderId)
                    }
                    composable(
                        route = Routes.NOTE_DETAIL,
                        arguments = listOf(navArgument(Routes.NOTE_DETAIL_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val noteId = entry.arguments?.getString(Routes.NOTE_DETAIL_ARG).orEmpty()
                        NoteDetailScreen(navController, noteId)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
