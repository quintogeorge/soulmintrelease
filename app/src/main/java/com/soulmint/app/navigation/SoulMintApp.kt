package com.soulmint.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.soulmint.domain.SoulMintViewModel
import com.soulmint.ui.screens.AppShell
import com.soulmint.ui.screens.AuthScreen
import com.soulmint.ui.screens.AvatarGeneratingScreen
import com.soulmint.ui.screens.AvatarPickScreen
import com.soulmint.ui.screens.ChatDetailScreen
import com.soulmint.ui.screens.ChatListScreen
import com.soulmint.ui.screens.DreamInputScreen
import com.soulmint.ui.screens.EditProfileScreen
import com.soulmint.ui.screens.FeedScreen
import com.soulmint.ui.screens.MatchScreen
import com.soulmint.ui.screens.MintPreviewScreen
import com.soulmint.ui.screens.MintSuccessScreen
import com.soulmint.ui.screens.MintingScreen
import com.soulmint.ui.screens.ProfileScreen
import com.soulmint.ui.screens.RegenerateAvatarScreen
import com.soulmint.ui.screens.ReportScreen
import com.soulmint.ui.screens.SelfDescribeScreen
import com.soulmint.ui.screens.SettingsScreen
import com.soulmint.ui.screens.TagConfirmScreen
import com.soulmint.ui.screens.WelcomeScreen

object Routes {
    const val Welcome = "welcome"
    const val Auth = "auth"
    const val SelfDescribe = "self"
    const val Dream = "dream"
    const val Tags = "tags"
    const val Generating = "generating"
    const val AvatarPick = "avatar_pick"
    const val Preview = "preview"
    const val Minting = "minting"
    const val Success = "success"
    const val Feed = "feed"
    const val Match = "match"
    const val Chats = "chats"
    const val ChatDetail = "chat/{chatId}"
    const val Profile = "profile"
    const val EditProfile = "edit_profile"
    const val RegenerateAvatar = "regenerate_avatar"
    const val Settings = "settings"
    const val Report = "report"
}

@Composable
fun SoulMintApp(viewModel: SoulMintViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val state = viewModel.uiState
    val tabsVisible = route in setOf(Routes.Feed, Routes.Chats, Routes.Profile, Routes.Settings)

    LaunchedEffect(state.authUid, state.isSyncingRemoteData, state.hasMintedProfile) {
        when (viewModel.preferredPostAuthRoute()) {
            "feed" -> {
                if (route !in setOf(Routes.Feed, Routes.Chats, Routes.Profile, Routes.Settings, Routes.ChatDetail, Routes.Report)) {
                    navController.navigate(Routes.Feed) {
                        popUpTo(Routes.Welcome) { inclusive = true }
                    }
                }
            }
            "self" -> {
                if (!state.authUid.isNullOrBlank() && route == Routes.Auth) {
                    navController.navigate(Routes.SelfDescribe) {
                        popUpTo(Routes.Welcome) { inclusive = false }
                    }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AppShell(tabsVisible = tabsVisible, currentRoute = route, onTabSelected = { navController.navigate(it) }) { innerPadding ->
            NavHost(navController = navController, startDestination = Routes.Welcome, modifier = Modifier.fillMaxSize()) {
                composable(Routes.Welcome) { WelcomeScreen(innerPadding) { navController.navigate(Routes.Auth) } }
                composable(Routes.Auth) { AuthScreen(innerPadding, viewModel) }
                composable(Routes.SelfDescribe) { SelfDescribeScreen(innerPadding, viewModel) { navController.navigate(Routes.Dream) } }
                composable(Routes.Dream) { DreamInputScreen(innerPadding, viewModel) { navController.navigate(Routes.Tags) } }
                composable(Routes.Tags) { TagConfirmScreen(innerPadding, viewModel) { navController.navigate(Routes.Generating) } }
                composable(Routes.Generating) { AvatarGeneratingScreen(innerPadding, viewModel) { navController.navigate(Routes.AvatarPick) } }
                composable(Routes.AvatarPick) { AvatarPickScreen(innerPadding, viewModel) { navController.navigate(Routes.Preview) } }
                composable(Routes.Preview) { MintPreviewScreen(innerPadding, viewModel) { navController.navigate(Routes.Minting) } }
                composable(Routes.Minting) { MintingScreen(innerPadding, viewModel) { navController.navigate(Routes.Success) } }
                composable(Routes.Success) { MintSuccessScreen(innerPadding, viewModel) { navController.navigate(Routes.Feed) } }
                composable(Routes.Feed) {
                    FeedScreen(
                        innerPadding,
                        viewModel,
                        onMatched = { chatId -> navController.navigate("chat/$chatId") },
                        onReport = { navController.navigate(Routes.Report) }
                    )
                }
                composable(Routes.Match) {
                    MatchScreen(innerPadding) {
                        val latestChatId = viewModel.consumeLatestMatchedChatId()
                        if (latestChatId != null) {
                            navController.navigate("chat/$latestChatId")
                        } else {
                            navController.navigate(Routes.Chats)
                        }
                    }
                }
                composable(Routes.Chats) { ChatListScreen(innerPadding, viewModel) { navController.navigate("chat/$it") } }
                composable(Routes.ChatDetail, arguments = listOf(navArgument("chatId") { type = NavType.StringType })) {
                    ChatDetailScreen(
                        innerPadding,
                        viewModel,
                        it.arguments?.getString("chatId").orEmpty(),
                        onReport = { navController.navigate(Routes.Report) },
                        onDeleted = { navController.popBackStack() }
                    )
                }
                composable(Routes.Profile) {
                    ProfileScreen(innerPadding, viewModel, onEdit = { navController.navigate(Routes.EditProfile) }, onRegenerate = { navController.navigate(Routes.RegenerateAvatar) })
                }
                composable(Routes.EditProfile) { EditProfileScreen(innerPadding, viewModel) { navController.popBackStack() } }
                composable(Routes.RegenerateAvatar) { RegenerateAvatarScreen(innerPadding, viewModel) { navController.popBackStack() } }
                composable(Routes.Settings) {
                    SettingsScreen(
                        innerPadding = innerPadding,
                        viewModel = viewModel,
                        onReport = {
                            viewModel.prepareReportFromContext()
                            navController.navigate(Routes.Report)
                        },
                        onSignedOut = {
                            navController.navigate(Routes.Auth) {
                                popUpTo(Routes.Welcome)
                            }
                        }
                    )
                }
                composable(Routes.Report) { ReportScreen(innerPadding, viewModel) { navController.popBackStack() } }
            }
        }
    }
}
