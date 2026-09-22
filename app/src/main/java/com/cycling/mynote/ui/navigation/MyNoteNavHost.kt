package com.cycling.mynote.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cycling.mynote.ui.capture.QuickCaptureScreen
import com.cycling.mynote.ui.diary.DiaryScreen
import com.cycling.mynote.ui.editor.EditorScreen
import com.cycling.mynote.ui.editor.EditorViewModel
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.library.LibraryScreen
import com.cycling.mynote.ui.onboarding.RepoAuthScreen
import com.cycling.mynote.ui.search.SearchScreen
import com.cycling.mynote.ui.settings.SettingsScreen
import com.cycling.mynote.ui.theme.MyNoteTheme

/** Every destination in the app. Routes are plain strings, and note ids are path-encoded. */
object MyNoteRoutes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val DIARY = "diary"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture"

    const val EDITOR_ARG_NOTE_ID = EditorViewModel.ARG_NOTE_ID
    const val EDITOR = "editor/{$EDITOR_ARG_NOTE_ID}"

    /** Note ids are repository-relative paths, so they contain slashes that must be encoded. */
    fun editor(noteId: String): String = "editor/${Uri.encode(noteId)}"
}

/**
 * The app's navigation graph.
 *
 * The start destination is chosen once [AppState.ready] — that is, once the persisted folder grant
 * has been read — so a returning user goes straight to the library and a new one to onboarding,
 * with no intermediate frame either way.
 *
 * Opening a note pushes the editor on top of whatever tab is showing, so the back gesture returns
 * to the list the note came from rather than to a fixed tab.
 */
@Composable
fun MyNoteNavHost(
    appViewModel: AppStateViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val currentRoute by navController.currentBackStackEntryAsState()

    // A released repository has to send the user back to onboarding: every other screen would be
    // looking at a folder that no longer exists.
    LaunchedEffect(appState.ready, appState.repoGranted) {
        if (!appState.ready) return@LaunchedEffect
        val route = currentRoute?.destination?.route
        if (!appState.repoGranted && route != null && route != MyNoteRoutes.ONBOARDING) {
            navController.navigate(MyNoteRoutes.ONBOARDING) { popUpTo(0) }
        }
    }

    // An incoming share takes precedence over wherever the app was; consuming it clears the flag.
    LaunchedEffect(appState.pendingShare, appState.repoGranted) {
        if (appState.pendingShare && appState.repoGranted) {
            navController.navigate(MyNoteRoutes.CAPTURE) { launchSingleTop = true }
        }
    }

    // The grant is read asynchronously, so there are a frame or two before the start destination is
    // known. Showing the brand rather than nothing keeps that from reading as a blank flash.
    if (!appState.ready) {
        AppStartupPlaceholder()
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (appState.repoGranted) MyNoteRoutes.LIBRARY else MyNoteRoutes.ONBOARDING,
    ) {
        composable(MyNoteRoutes.ONBOARDING) {
            RepoAuthScreen(
                onAuthorized = {
                    navController.navigate(MyNoteRoutes.LIBRARY) {
                        popUpTo(MyNoteRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(MyNoteRoutes.LIBRARY) {
            LibraryScreen(
                onOpenNote = { noteId -> navController.navigate(MyNoteRoutes.editor(noteId)) },
                onOpenSearch = { navController.switchTab(MyNoteRoutes.SEARCH) },
                onOpenDiary = { navController.switchTab(MyNoteRoutes.DIARY) },
                onOpenSettings = { navController.switchTab(MyNoteRoutes.SETTINGS) },
                onOpenCapture = { navController.navigate(MyNoteRoutes.CAPTURE) },
            )
        }

        composable(MyNoteRoutes.SEARCH) {
            SearchScreen(
                onOpenNote = { noteId -> navController.navigate(MyNoteRoutes.editor(noteId)) },
                onOpenLibrary = { navController.switchTab(MyNoteRoutes.LIBRARY) },
                onOpenDiary = { navController.switchTab(MyNoteRoutes.DIARY) },
                onOpenSettings = { navController.switchTab(MyNoteRoutes.SETTINGS) },
                onOpenCapture = { navController.navigate(MyNoteRoutes.CAPTURE) },
            )
        }

        composable(MyNoteRoutes.DIARY) {
            DiaryScreen(
                onOpenNote = { noteId -> navController.navigate(MyNoteRoutes.editor(noteId)) },
                onOpenLibrary = { navController.switchTab(MyNoteRoutes.LIBRARY) },
                onOpenSearch = { navController.switchTab(MyNoteRoutes.SEARCH) },
                onOpenSettings = { navController.switchTab(MyNoteRoutes.SETTINGS) },
            )
        }

        composable(MyNoteRoutes.SETTINGS) {
            SettingsScreen(
                onOpenLibrary = { navController.switchTab(MyNoteRoutes.LIBRARY) },
                onOpenSearch = { navController.switchTab(MyNoteRoutes.SEARCH) },
                onOpenDiary = { navController.switchTab(MyNoteRoutes.DIARY) },
            )
        }

        composable(
            route = MyNoteRoutes.EDITOR,
            arguments = listOf(navArgument(MyNoteRoutes.EDITOR_ARG_NOTE_ID) { type = NavType.StringType }),
        ) {
            EditorScreen(onBack = { navController.popBackStack() })
        }

        composable(MyNoteRoutes.CAPTURE) {
            QuickCaptureScreen(
                onClose = { navController.popBackStack() },
                onOpenNote = { noteId ->
                    navController.popBackStack()
                    navController.navigate(MyNoteRoutes.editor(noteId))
                },
            )
        }
    }
}

/**
 * Moves between the bottom-bar destinations.
 *
 * A single destination is kept on the back stack — `launchSingleTop` plus popping up to the graph's
 * start — so switching tabs four times does not leave four entries to back through, which is what
 * a bottom bar implies.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The brand mark, shown for the moment before the persisted grant has been read. */
@Composable
private fun AppStartupPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyNoteTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MyNoteIcons.feather,
            contentDescription = null,
            tint = MyNoteTheme.colors.textTertiary,
            modifier = Modifier.size(32.dp),
        )
    }
}
