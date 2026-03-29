package com.soulmint.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.soulmint.R
import com.soulmint.data.repository.SoulMintRepository
import com.soulmint.data.repository.SolanaWalletAdapterConnector
import com.soulmint.data.repository.SolanaRpcGateway
import com.soulmint.data.repository.PreparedWalletTransaction
import com.soulmint.data.repository.WalletAuthorizationSession
import com.soulmint.domain.SoulMintViewModel
import com.soulmint.navigation.Routes
import com.soulmint.ui.components.AvatarArt
import com.soulmint.ui.components.AvatarVariantTile
import com.soulmint.ui.components.HeroHeader
import com.soulmint.ui.components.MatchCard
import com.soulmint.ui.components.MintStepper
import com.soulmint.ui.components.NftCard
import com.soulmint.ui.components.ScreenContainer
import com.soulmint.ui.components.TagGroup
import com.soulmint.ui.theme.AccentGlow
import com.soulmint.ui.theme.AccentGold
import com.soulmint.ui.theme.AccentRose
import com.soulmint.ui.theme.AccentViolet
import com.soulmint.ui.theme.BgElevated
import com.soulmint.ui.theme.BgPrimary
import com.soulmint.ui.theme.BorderSubtle
import com.soulmint.ui.theme.TextMuted
import com.soulmint.ui.theme.TextPrimary
import com.soulmint.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    tabsVisible: Boolean,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            if (tabsVisible) {
                NavigationBar(containerColor = Color(0xFF101018), modifier = Modifier.navigationBarsPadding()) {
                    listOf(
                        Triple(Routes.Feed, "Feed", Icons.Default.Home),
                        Triple(Routes.Chats, "Chats", Icons.Default.ChatBubble),
                        Triple(Routes.Profile, "Profile", Icons.Default.Person),
                        Triple(Routes.Settings, "Settings", Icons.Default.Settings)
                    ).forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { onTabSelected(route) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF15121F), Color(0xFF0A0A0F)))
            )
        ) {
            content(innerPadding)
        }
    }
}

@Composable
fun WelcomeScreen(innerPadding: PaddingValues, onContinue: () -> Unit) {
    ScreenContainer(innerPadding) {
        Spacer(modifier = Modifier.height(24.dp))
        HeroHeader("SoulMint", "Mint your identity. Let the app match the soul behind the words.")
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171521))) {
            Box(
                modifier = Modifier.fillMaxWidth().height(380.dp).background(
                Brush.radialGradient(listOf(AccentGlow.copy(alpha = 0.35f), AccentRose.copy(alpha = 0.2f), Color.Transparent))
                )
            ) {
                Text(
                    "SoulMint turns your words into identity, your identity into art, and art into a more intimate way to connect.",
                    modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)) {
            Text("Begin")
        }
    }
}

@Composable
fun AuthScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel) {
    val state = viewModel.uiState
    val activity = LocalContext.current.findActivity()
    val isWaitingForWallet = state.isAuthenticating && !state.isSyncingRemoteData
    val isSigningIn = state.isSyncingRemoteData
    val buttonLabel = when {
        isSigningIn -> "Signing You In..."
        isWaitingForWallet -> "Waiting For Wallet Approval..."
        else -> "Connect Solana Wallet"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF0A0A0F))
        ) {
            Image(
                painter = painterResource(id = R.drawable.soulmint_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.5f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xE60A0A0F),
                                AccentViolet.copy(alpha = 0.12f),
                                Color(0xF20A0A0F)
                            )
                        )
                    )
            )
        }
        ScreenContainer(PaddingValues()) {
            HeroHeader("Enter Quietly", "Connect your Solana wallet to sign in. The wallet becomes your identity here.")
            if (isSigningIn) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgElevated.copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = AccentGlow)
                        Text("Matching in progress...", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Your wallet is connected. We are restoring your profile and preparing the next introduction now.",
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(22.dp), color = BgElevated.copy(alpha = 0.88f), border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("A single wallet signature unlocks your SoulMint identity and brings you back to your saved world.", color = TextSecondary)
                    }
                }
            }
            Button(
                onClick = {
                    val hostActivity = activity ?: return@Button
                    viewModel.signInWithWallet(hostActivity)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isAuthenticating && !state.isSyncingRemoteData && activity != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isSigningIn -> AccentRose
                        isWaitingForWallet -> AccentGlow
                        else -> AccentViolet
                    },
                    disabledContainerColor = when {
                        isSigningIn -> AccentRose.copy(alpha = 0.72f)
                        isWaitingForWallet -> AccentGlow.copy(alpha = 0.72f)
                        else -> AccentViolet.copy(alpha = 0.72f)
                    }
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                    }
                    Text(buttonLabel)
                }
            }
            state.authError?.let { Text(it, color = AccentRose) }
            if (state.isAuthenticating && state.isSyncingRemoteData) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Signing you in...", color = TextSecondary)
                }
            }
            if (state.isAuthenticating && !state.isSyncingRemoteData) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Connection started. Approve the request in your wallet to continue.", color = TextSecondary)
                }
            }
            state.remoteDataError?.let { Text(it, color = AccentRose) }
            Text(
                "SoulMint now signs in with your Solana wallet. Email and anonymous access are no longer shown here.",
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SelfDescribeScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    PromptInputScreen(
        innerPadding, "Describe Yourself",
        "Personality, style, vibe, and appearance in words only. This becomes your portrait prompt.",
        state.selfDescription, "${state.selfDescription.length}/600",
        "Minimum 30 characters. No photos. Vivid details work best.",
        viewModel::updateSelfDescription,
        onContinue,
        beforeEditor = {
            GenderSelector(
                selectedGender = state.selectedGender,
                onSelect = viewModel::selectGender
            )
        },
        canContinue = state.selectedGender != null
    )
}

@Composable
fun DreamInputScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    PromptInputScreen(
        innerPadding, "Describe Your Dream Partner",
        "Say what kind of energy, values, and chemistry you want to find.",
        state.dreamDescription, "${state.dreamDescription.length}/500",
        "Minimum 20 characters. This helps shape the kind of connection you want to meet.",
        viewModel::updateDreamDescription, onContinue
    )
}

@Composable
private fun PromptInputScreen(
    innerPadding: PaddingValues,
    title: String,
    subtitle: String,
    value: String,
    counter: String,
    helper: String,
    onValueChange: (String) -> Unit,
    onContinue: () -> Unit,
    beforeEditor: (@Composable () -> Unit)? = null,
    canContinue: Boolean = true
) {
    ScreenContainer(innerPadding) {
        HeroHeader(title, subtitle)
        beforeEditor?.invoke()
        Surface(shape = RoundedCornerShape(22.dp), color = BgElevated, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(helper, color = TextMuted, modifier = Modifier.weight(1f))
                    Text(counter, color = TextSecondary)
                }
            }
        }
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun GenderSelector(
    selectedGender: String?,
    onSelect: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BgElevated)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("I am", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("female" to "Woman", "male" to "Man").forEach { (value, label) ->
                    val selected = selectedGender == value
                    val colors = if (selected) {
                        ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                    if (selected) {
                        Button(
                            onClick = { onSelect(value) },
                            modifier = Modifier.weight(1f),
                            colors = colors
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(value) },
                            modifier = Modifier.weight(1f),
                            colors = colors
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
fun TagConfirmScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Confirm The Signal", "Claude-derived tags are editable before embeddings and mint metadata are locked in.")
        TagGroup("About me", state.selfTags, viewModel::toggleSelfTag)
        TagGroup("Looking for", state.dreamTags, viewModel::toggleDreamTag)
        Button(
            onClick = {
                viewModel.generateAvatarVariants()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
        ) { Text("Generate Portrait") }
    }
}

@Composable
fun AvatarGeneratingScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onComplete: () -> Unit) {
    val state = viewModel.uiState
    LaunchedEffect(state.isGeneratingAvatars, state.avatarGenerationError, state.selectedAvatarId) {
        if (!state.isGeneratingAvatars && state.avatarGenerationError == null && state.selectedAvatarId != null) {
            onComplete()
        }
    }
    ScreenContainer(innerPadding) {
        HeroHeader("Crafting Your Soul", "Your portrait is being prepared now.")
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(
                Brush.radialGradient(listOf(AccentGlow.copy(alpha = 0.4f), AccentViolet.copy(alpha = 0.25f), Color.Transparent)),
                RoundedCornerShape(28.dp)
            )
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentGlow, modifier = Modifier.size(72.dp).align(Alignment.Center))
        }
        Card(colors = CardDefaults.cardColors(containerColor = BgElevated)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SoulMint is currently in phase one.", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Right now, introductions are curated for a smoother first experience while the community grows.",
                    color = TextSecondary
                )
                Text(
                    "Right now, most early users are men 🙂 Once the community is larger and the gender balance feels healthier, phase two will unlock real person-to-person matching.",
                    color = TextSecondary
                )
            }
        }
        if (state.isGeneratingAvatars) {
            Text("Waiting for Firebase Cloud Functions to finish generating your portrait...", color = AccentGlow)
            Text("Quietly matching your first introduction at the same time...", color = TextSecondary)
        }
        state.avatarGenerationError?.let {
            Text(it, color = AccentRose)
        }
    }
}

@Composable
fun AvatarPickScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Confirm Your Portrait", "Cloud Functions returns one moderated portrait to review before minting.")
        if (state.isGeneratingAvatars) Text("Generating with Firebase Cloud Functions...", color = AccentGlow)
        state.avatarGenerationError?.let { Text(it, color = AccentRose) }
        state.avatarVariants.firstOrNull()?.let { avatar ->
            AvatarVariantTile(
                avatar = avatar,
                selected = avatar.id == state.selectedAvatarId,
                onClick = { viewModel.selectAvatar(avatar.id) }
            )
            Text(
                text = avatar.previewUrl ?: "No preview URL yet",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedButton(onClick = viewModel::regenerateAvatars, enabled = state.regenCredits > 0 && !state.isGeneratingAvatars, modifier = Modifier.fillMaxWidth()) {
            Text("Generate New Portrait (${state.regenCredits} left)")
        }
        Button(onClick = onContinue, enabled = !state.isGeneratingAvatars && state.selectedAvatarId != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)) {
            Text("Use This Portrait")
        }
    }
}

@Composable
fun MintPreviewScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    val hasGeneratedAvatar = !state.profile?.avatar?.previewUrl.isNullOrBlank()
    ScreenContainer(innerPadding) {
        HeroHeader("Mint Your Soul", "Once your portrait is ready, one tap prepares the mint and opens the wallet signing flow.")
        state.profile?.let { NftCard(it) }
        if (!hasGeneratedAvatar) {
            Text("Generate your avatar first. Mint stays locked until a portrait is ready.", color = AccentRose)
        }
        state.mintError?.let { Text(it, color = AccentRose) }
        state.walletConnectError?.let { Text(it, color = AccentRose) }
        Button(
            onClick = onContinue,
            enabled = hasGeneratedAvatar && !state.isGeneratingAvatars && !state.isMintingProfile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
        ) {
            Text(if (state.isMintingProfile) "Minting..." else "Mint")
        }
    }
}

@Composable
fun MintingScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onComplete: () -> Unit) {
    val tag = "SoulMintDebug"
    val repository = remember { SoulMintRepository() }
    val context = LocalContext.current
    val connector = remember { SolanaWalletAdapterConnector() }
    val solanaRpcGateway = remember { SolanaRpcGateway() }
    val state = viewModel.uiState
    var signingRequested by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (!state.isMintFlowRunning && state.mintMode != "solana_confirmed") {
            val activity = context.findActivity()
            if (activity == null) {
                viewModel.walletConnectionFailed("Unable to find an Activity for wallet authorization.")
            } else {
                viewModel.beginMintFlow()
                activity.lifecycleScope.launch {
                    runCatching {
                        viewModel.setMintStep(1)
                        signingRequested = true
                        val signed = connector.executeAuthorizedMintFlow(
                            activity = activity,
                            existingAuthorization = WalletAuthorizationSession(
                                ownerWalletAddress = viewModel.uiState.solanaWalletAddress.orEmpty(),
                                authToken = viewModel.uiState.solanaWalletAuthToken
                            )
                        ) { authorization ->
                            viewModel.connectSolanaWallet(
                                walletAddress = authorization.ownerWalletAddress,
                                authToken = authorization.authToken
                            )
                            viewModel.setMintStep(2)
                            solanaRpcGateway.requireSufficientMainnetBalance(authorization.ownerWalletAddress)
                            viewModel.prepareMintProfileForFlow()
                            val transactionBase64 = viewModel.uiState.mintPreparedTransactionBase64
                                ?: error("Mint preparation completed without a transaction payload.")
                            viewModel.setMintStep(4)
                            PreparedWalletTransaction(
                                transactionBase64 = transactionBase64,
                                minContextSlot = viewModel.uiState.mintPreparedMinContextSlot
                            )
                        }
                        viewModel.connectSolanaWallet(
                            walletAddress = signed.authorization.ownerWalletAddress,
                            authToken = signed.authorization.authToken
                        )
                        viewModel.setMintStep(3)
                        viewModel.confirmSubmittedMint(signed.signature, onComplete)
                    }.onFailure { error ->
                        signingRequested = false
                        val message = error.message ?: "Unable to continue minting on Solana mainnet (${error::class.java.simpleName})."
                        if (viewModel.uiState.mintPreparedTransactionBase64.isNullOrBlank()) {
                            Log.e(tag, "mintFlow:startFailure ${error::class.java.name}: ${error.message}", error)
                            viewModel.mintFlowFailed(message)
                        } else {
                            Log.e(tag, "mintFlow:signFailure ${error::class.java.name}: ${error.message}", error)
                            viewModel.walletConnectionFailed(message)
                        }
                    }
                }
            }
        }
        onDispose { }
    }

    val subtitle = when {
        state.isMintingProfile -> "Preparing metadata and mint transaction."
        state.isConfirmingMintSubmission -> "Wallet signature received. Waiting for Solana mainnet confirmation."
        signingRequested && state.mintTxHash.isNullOrBlank() -> "Waiting for your wallet to sign and send."
        else -> "SoulMint handles the setup, then hands off to your wallet only for approval."
    }

    ScreenContainer(innerPadding) {
        HeroHeader("Minting", subtitle)
        MintStepper(repository.mintSteps(state.mintStep))
        state.solanaWalletAddress?.let { SettingsRow("Wallet", it) }
        state.mintError?.let { Text(it, color = AccentRose) }
        state.walletConnectError?.let { Text(it, color = AccentRose) }
    }
}

@Composable
fun MintSuccessScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onContinue: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Mint Complete", "Your portrait has been prepared and submitted through the wallet flow.")
        state.profile?.let { NftCard(it) }
        state.mintTxHash?.takeIf { it.isNotBlank() }?.let { SettingsRow("Transaction signature", it) }
        state.mintMetadataIpfsUrl?.let { SettingsRow("Metadata", it) }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRose)) {
            Text("Enter Match Feed")
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun FeedScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onMatched: (String) -> Unit, onReport: () -> Unit) {
    val currentProfile = viewModel.uiState.feed.getOrNull(viewModel.uiState.currentFeedIndex)
    val state = viewModel.uiState
    val isMatching = currentProfile == null
    val context = LocalContext.current
    ScreenContainer(innerPadding) {
        state.usagePaywall?.let { paywall ->
            UsagePaywallCard(
                paywall = paywall,
                isChecking = state.isCheckingUsagePayment,
                onDismiss = viewModel::dismissUsagePaywall,
                onCheck = viewModel::confirmUsagePayment,
                onOpenSol = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paywall.solanaPaySolUrl)))
                },
                onOpenSkr = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paywall.solanaPaySkrUrl)))
                }
            )
        }
        currentProfile?.let { profile ->
            val alreadyMatched = state.chats.any { it.id == profile.id }
            MatchCard(profile)
            if (alreadyMatched) {
                Text(
                    "You already opened this connection. Pass if you want to move to the next introduction.",
                    color = TextSecondary
                )
            }
        }
        if (currentProfile == null) {
            Card(colors = CardDefaults.cardColors(containerColor = BgElevated), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AccentGlow.copy(alpha = 0.38f),
                                        AccentViolet.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = AccentGlow)
                                Text(
                                    if (state.isPassingFeed) "Pass received. Finding someone new..." else "Matching you with someone who fits your signal...",
                                    color = TextPrimary
                                )
                                Text(
                                    if (state.isPassingFeed) "Your next introduction is being prepared now." else "The next introduction is on the way.",
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        state.remoteDataError?.let { Text(it, color = AccentRose) }
        currentProfile?.let { profile ->
            val alreadyMatched = state.chats.any { it.id == profile.id }
            if (!isMatching) {
                if (alreadyMatched) {
                    OutlinedButton(
                        onClick = viewModel::passCurrentFeed,
                        enabled = !state.isPassingFeed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isPassingFeed) "Passing..." else "Pass")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = viewModel::passCurrentFeed,
                            enabled = !state.isPassingFeed,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (state.isPassingFeed) "Passing..." else "Pass")
                        }
                        Button(onClick = { viewModel.likeCurrentFeed(true, onMatched) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGold)) { Text("Super Like") }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchScreen(innerPadding: PaddingValues, onContinue: () -> Unit) {
    ScreenContainer(innerPadding) {
        HeroHeader("It’s Mutual", "You can move straight into conversation.")
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(
                Brush.radialGradient(listOf(AccentRose.copy(alpha = 0.45f), AccentGlow.copy(alpha = 0.25f), Color.Transparent)),
                RoundedCornerShape(28.dp)
            )
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentRose, modifier = Modifier.size(80.dp).align(Alignment.Center))
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Start Chat") }
    }
}

@Composable
fun ChatListScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onOpenChat: (String) -> Unit) {
    val state = viewModel.uiState
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp, vertical = 16.dp)) {
        HeroHeader("Chats", "Your conversations stay here once a connection opens.")
        Spacer(modifier = Modifier.height(16.dp))
        if (state.chats.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = BgElevated), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No conversations yet", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Once you open a connection from the feed, it will appear here.", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.chats) { chat ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgElevated),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenChat(chat.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            chat.avatar?.let { avatar ->
                                AvatarArt(
                                    avatar = avatar,
                                    modifier = Modifier.size(56.dp),
                                    label = null
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(chat.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(chat.lastMessage, color = TextSecondary)
                            }
                            Text(chat.timestamp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(
    innerPadding: PaddingValues,
    viewModel: SoulMintViewModel,
    chatId: String,
    onReport: () -> Unit,
    onDeleted: () -> Unit
) {
    val state = viewModel.uiState
    val activeChat = state.chats.firstOrNull { it.id == chatId }
    val context = LocalContext.current
    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
        viewModel.prepareReportForChat(chatId)
    }
    ScreenContainer(innerPadding) {
        state.usagePaywall?.let { paywall ->
            UsagePaywallCard(
                paywall = paywall,
                isChecking = state.isCheckingUsagePayment,
                onDismiss = viewModel::dismissUsagePaywall,
                onCheck = viewModel::confirmUsagePayment,
                onOpenSol = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paywall.solanaPaySolUrl)))
                },
                onOpenSkr = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paywall.solanaPaySkrUrl)))
                }
            )
        }
        Card(colors = CardDefaults.cardColors(containerColor = BgElevated), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeChat?.avatar?.let { avatar ->
                    AvatarArt(
                        avatar = avatar,
                        modifier = Modifier.size(72.dp),
                        label = null
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(activeChat?.name ?: "Conversation", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                    Text("Take your time. Let it unfold naturally.", color = TextSecondary)
                }
            }
        }
        OutlinedButton(
            onClick = { viewModel.deleteActiveChat(onDeleted) },
            enabled = !state.isDeletingChat,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isDeletingChat) "Deleting Chat..." else "Delete Chat")
        }
        Card(colors = CardDefaults.cardColors(containerColor = BgElevated), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.activeChatMessages.isEmpty()) {
                    Text("No messages yet. Start the conversation.", color = TextSecondary)
                } else {
                    state.activeChatMessages.forEach { message ->
                        Text(
                            "${if (message.isMine) "You" else message.senderName}: ${message.content}",
                            color = if (message.isMine) AccentGlow else TextPrimary
                        )
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = BgElevated, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(
                    value = state.chatDraft,
                    onValueChange = viewModel::updateChatDraft,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
                Button(onClick = viewModel::sendChatMessage, modifier = Modifier.fillMaxWidth()) {
                    Text("Send")
                }
            }
        }
        state.remoteDataError?.let { Text(it, color = AccentRose) }
    }
}

@Composable
private fun UsagePaywallCard(
    paywall: com.soulmint.data.model.UsagePaywall,
    isChecking: Boolean,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onOpenSol: () -> Unit,
    onOpenSkr: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BgElevated), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Continue with a paid unlock", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                if (paywall.trigger == "pass_limit") {
                    "You have used the current free pass limit. Pay to keep meeting new people."
                } else {
                    "You have used the current free message limit. Pay to keep the conversation going."
                },
                color = TextSecondary
            )
            Button(onClick = onOpenSol, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGold)) {
                Text("Pay 0.05 SOL")
            }
            OutlinedButton(onClick = onOpenSkr, modifier = Modifier.fillMaxWidth()) {
                Text("Pay 200 SKR")
            }
            OutlinedButton(onClick = onCheck, enabled = !isChecking, modifier = Modifier.fillMaxWidth()) {
                Text(if (isChecking) "Checking Payment..." else "I Paid, Check Status")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not Now")
            }
        }
    }
}

@Composable
fun ProfileScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onEdit: () -> Unit, onRegenerate: () -> Unit) {
    ScreenContainer(innerPadding) {
        HeroHeader("Your NFT", "Profile visibility, description updates, and avatar regeneration all branch from here.")
        viewModel.uiState.profile?.let { NftCard(it) }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit Descriptions") }
        OutlinedButton(onClick = onRegenerate, modifier = Modifier.fillMaxWidth()) { Text("Generate New Portrait") }
    }
}

@Composable
fun EditProfileScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onDone: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Edit Profile", "Description changes should trigger re-embedding and optionally metadata updates.")
        PromptEditor("Self description", state.selfDescription, viewModel::updateSelfDescription)
        PromptEditor("Dream description", state.dreamDescription, viewModel::updateDreamDescription)
        Button(onClick = { viewModel.saveProfile(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@Composable
fun RegenerateAvatarScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onDone: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Generate New Portrait", "This screen mirrors the Cloud Functions generation loop without exposing image API secrets on device.")
        state.avatarVariants.firstOrNull { it.id == state.selectedAvatarId }?.let {
            AvatarArt(it, modifier = Modifier.fillMaxWidth().height(280.dp), label = "Current selection")
        }
        OutlinedButton(onClick = viewModel::regenerateAvatars, enabled = state.regenCredits > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Generate New Portrait (${state.regenCredits} left)")
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    viewModel: SoulMintViewModel,
    onReport: () -> Unit,
    onSignedOut: () -> Unit
) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Settings", "Privacy, embedded wallet visibility, notifications, and destructive account actions live here.")
        SettingsRow("NFT visibility", "Public / Match-only / Hidden")
        SettingsRow("Wallet", "Hidden by default, reveal for advanced users")
        SettingsRow("Notifications", "Matches, chats, milestones")
        TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("Open Report / Block Flow") }
        OutlinedButton(onClick = { viewModel.signOut(onSignedOut) }, modifier = Modifier.fillMaxWidth()) {
            Text("Sign Out")
        }
        Button(
            onClick = { viewModel.deleteMyAccount(onSignedOut) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isDeletingAccount,
            colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
        ) {
            Text(if (state.isDeletingAccount) "Deleting Account..." else "Delete Account")
        }
        state.remoteDataError?.let { Text(it, color = AccentRose) }
    }
}

@Composable
fun ReportScreen(innerPadding: PaddingValues, viewModel: SoulMintViewModel, onDone: () -> Unit) {
    val state = viewModel.uiState
    ScreenContainer(innerPadding) {
        HeroHeader("Report Or Block", "Reports can become moderation queue entries and on-chain flags.")
        SettingsRow(
            "Target",
            state.reportTargetName ?: "No active target selected yet"
        )
        SettingsRow("Common reasons", "Harassment, impersonation, explicit prompts, scam behavior")
        Surface(shape = RoundedCornerShape(20.dp), color = BgElevated, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Why are you reporting this user?", color = TextMuted)
                BasicTextField(
                    value = state.reportReason,
                    onValueChange = viewModel::updateReportReason,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    decorationBox = { inner ->
                        if (state.reportReason.isBlank()) {
                            Text("Describe the issue briefly", color = TextMuted)
                        }
                        inner()
                    }
                )
            }
        }
        state.reportError?.let { Text(it, color = AccentRose) }
        Button(
            onClick = { viewModel.submitReportAndBlock(onDone) },
            enabled = !state.isReporting && state.reportTargetId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isReporting) "Submitting..." else "Submit And Block")
        }
    }
}

@Composable
private fun PromptEditor(title: String, value: String, onValueChange: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = BgElevated, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = TextMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = BgElevated)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSecondary)
        }
    }
}
