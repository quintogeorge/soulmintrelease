package com.soulmint.domain

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.soulmint.data.model.AvatarPipelineStep
import com.soulmint.data.model.AvatarVariant
import com.soulmint.data.model.ChatMessage
import com.soulmint.data.model.ChatPreview
import com.soulmint.data.model.MatchProfile
import com.soulmint.data.model.ProfileCard
import com.soulmint.data.model.TraitTag
import com.soulmint.data.model.UsagePaywall
import com.soulmint.data.repository.AvatarGenerationGateway
import com.soulmint.data.repository.CheckUsagePaymentGateway
import com.soulmint.data.repository.DeleteChatThreadGateway
import com.soulmint.data.repository.DeleteMyAccountGateway
import com.soulmint.data.repository.FirebaseAuthRepository
import com.soulmint.data.repository.FirestoreSoulMintRepository
import com.soulmint.data.repository.MintSoulboundGateway
import com.soulmint.data.repository.RecordFeedActionGateway
import com.soulmint.data.repository.RefreshMatchFeedGateway
import com.soulmint.data.repository.ReportAndBlockGateway
import com.soulmint.data.repository.SendChatMessageGateway
import com.soulmint.data.repository.SolanaRpcGateway
import com.soulmint.data.repository.SoulMintRepository
import com.soulmint.data.repository.UsagePaymentRequiredException
import com.soulmint.data.repository.WalletSignInGateway
import kotlinx.coroutines.launch

data class SoulMintUiState(
    val authEmail: String = "",
    val authPassword: String = "",
    val selfDescription: String = "",
    val dreamDescription: String = "",
    val selfTags: List<TraitTag> = emptyList(),
    val dreamTags: List<TraitTag> = emptyList(),
    val avatarVariants: List<AvatarVariant> = emptyList(),
    val selectedAvatarId: String? = null,
    val avatarPipeline: List<AvatarPipelineStep> = emptyList(),
    val mintStep: Int = 0,
    val feed: List<MatchProfile> = emptyList(),
    val currentFeedIndex: Int = 0,
    val chats: List<ChatPreview> = emptyList(),
    val latestMatchedChatId: String? = null,
    val profile: ProfileCard? = null,
    val isPassingFeed: Boolean = false,
    val regenCredits: Int = 2,
    val isGeneratingAvatars: Boolean = false,
    val avatarGenerationError: String? = null,
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val authUid: String? = null,
    val selectedGender: String? = null,
    val isSyncingRemoteData: Boolean = false,
    val remoteDataError: String? = null,
    val hasMintedProfile: Boolean = false,
    val isMintingProfile: Boolean = false,
    val mintError: String? = null,
    val mintTxHash: String? = null,
    val mintMode: String? = null,
    val mintWalletAddress: String? = null,
    val mintMetadataIpfsUrl: String? = null,
    val mintExternalUrl: String? = null,
    val mintExternalPlatform: String? = null,
    val mintExternalNetwork: String? = null,
    val mintExternalGatewayUrl: String? = null,
    val mintPreparedTransactionBase64: String? = null,
    val mintPreparedMintAddress: String? = null,
    val mintPreparedMinContextSlot: Int? = null,
    val mintPreparedRpcUrl: String? = null,
    val isConfirmingMintSubmission: Boolean = false,
    val isMintFlowRunning: Boolean = false,
    val solanaWalletAddress: String? = null,
    val solanaWalletAuthToken: String? = null,
    val walletConnectError: String? = null,
    val activeChatId: String? = null,
    val activeChatMessages: List<ChatMessage> = emptyList(),
    val chatDraft: String = "",
    val usagePaywall: UsagePaywall? = null,
    val isCheckingUsagePayment: Boolean = false,
    val reportTargetId: String? = null,
    val reportTargetName: String? = null,
    val reportReason: String = "",
    val isReporting: Boolean = false,
    val reportError: String? = null,
    val isDeletingAccount: Boolean = false,
    val isDeletingChat: Boolean = false
)

class SoulMintViewModel(
    private val repository: SoulMintRepository = SoulMintRepository(),
    private val avatarGateway: AvatarGenerationGateway = AvatarGenerationGateway(),
    private val authRepository: FirebaseAuthRepository = FirebaseAuthRepository(),
    private val remoteRepository: FirestoreSoulMintRepository = FirestoreSoulMintRepository(),
    private val deleteChatThreadGateway: DeleteChatThreadGateway = DeleteChatThreadGateway(),
    private val mintGateway: MintSoulboundGateway = MintSoulboundGateway(),
    private val recordFeedActionGateway: RecordFeedActionGateway = RecordFeedActionGateway(),
    private val refreshMatchFeedGateway: RefreshMatchFeedGateway = RefreshMatchFeedGateway(),
    private val sendChatMessageGateway: SendChatMessageGateway = SendChatMessageGateway(),
    private val checkUsagePaymentGateway: CheckUsagePaymentGateway = CheckUsagePaymentGateway(),
    private val reportAndBlockGateway: ReportAndBlockGateway = ReportAndBlockGateway(),
    private val deleteMyAccountGateway: DeleteMyAccountGateway = DeleteMyAccountGateway(),
    private val solanaRpcGateway: SolanaRpcGateway = SolanaRpcGateway(),
    private val walletSignInGateway: WalletSignInGateway = WalletSignInGateway()
) : ViewModel() {
    private companion object {
        const val TAG = "SoulMintDebug"
    }

    private var chatsListener: ListenerRegistration? = null
    private var chatMessagesListener: ListenerRegistration? = null

    var uiState by mutableStateOf(
        defaultUiState(authRepository.currentSession()?.uid)
    )
        private set

    init {
        authRepository.currentSession()?.uid?.let { uid ->
            syncRemoteData(uid)
        }
    }

    fun signInAnonymously(onSuccess: () -> Unit) {
        if (uiState.isAuthenticating) return
        uiState = uiState.copy(isAuthenticating = true, authError = null, authUid = null)
        viewModelScope.launch {
            runCatching { authRepository.ensureAnonymousSession() }
                .onSuccess { session ->
                    uiState = uiState.copy(
                        isAuthenticating = false,
                        authError = null,
                        authUid = session.uid
                    )
                    startChatsObserver(session.uid)
                    syncRemoteData(session.uid)
                    onSuccess()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isAuthenticating = false,
                        authUid = null,
                        authError = error.message ?: "Unable to sign in with Firebase."
                    )
                }
        }
    }

    fun updateAuthEmail(value: String) {
        uiState = uiState.copy(authEmail = value)
    }

    fun updateAuthPassword(value: String) {
        uiState = uiState.copy(authPassword = value)
    }

    fun signInWithEmail(onSuccess: () -> Unit) {
        if (uiState.isAuthenticating) return
        uiState = uiState.copy(isAuthenticating = true, authError = null, authUid = null)
        viewModelScope.launch {
            runCatching {
                authRepository.signInOrRegisterWithEmail(
                    email = uiState.authEmail,
                    password = uiState.authPassword
                )
            }.onSuccess { session ->
                uiState = uiState.copy(
                    isAuthenticating = false,
                    authError = null,
                    authUid = session.uid
                )
                startChatsObserver(session.uid)
                syncRemoteData(session.uid)
                onSuccess()
            }.onFailure { error ->
                uiState = uiState.copy(
                    isAuthenticating = false,
                    authUid = null,
                    authError = error.message ?: "Unable to sign in with email."
                )
            }
        }
    }

    fun signInWithWallet(activity: Activity) {
        if (uiState.isAuthenticating) return
        uiState = uiState.copy(isAuthenticating = true, authError = null, authUid = null)
        viewModelScope.launch {
            runCatching { walletSignInGateway.signIn(activity) }
                .onSuccess { session ->
                    uiState = uiState.copy(
                        isAuthenticating = false,
                        authError = null,
                        authUid = session.uid
                    )
                    startChatsObserver(session.uid)
                    syncRemoteData(session.uid)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isAuthenticating = false,
                        authUid = null,
                        authError = error.message ?: "Unable to sign in with your wallet."
                    )
                }
        }
    }

    private fun syncRemoteData(uid: String) {
        startChatsObserver(uid)
        uiState = uiState.copy(isSyncingRemoteData = true, remoteDataError = null)
        viewModelScope.launch {
            runCatching {
                remoteRepository.bootstrapUser(
                    uid = uid,
                    defaultProfile = repository.myProfile(),
                    defaultSelfTags = repository.suggestedSelfTags(),
                    defaultDreamTags = repository.suggestedDreamTags()
                )
                runCatching { refreshMatchFeedGateway.refresh() }
                remoteRepository.loadState(
                    uid = uid,
                    fallbackProfile = repository.myProfile(),
                    fallbackSelfTags = repository.suggestedSelfTags(),
                    fallbackDreamTags = repository.suggestedDreamTags()
                )
            }.onSuccess { state ->
                uiState = uiState.copy(
                    isSyncingRemoteData = false,
                    remoteDataError = null,
                    profile = state.profile,
                    selectedGender = state.profile.gender,
                    selfTags = state.selfTags,
                    dreamTags = state.dreamTags,
                    feed = state.feed.ifEmpty { uiState.feed },
                    chats = state.chats.ifEmpty { uiState.chats },
                    hasMintedProfile = state.hasMintedProfile,
                    currentFeedIndex = 0,
                    selfDescription = state.profile.bio,
                    dreamDescription = state.profile.dreamDescription,
                    selectedAvatarId = state.profile.avatar.id,
                    avatarVariants = listOf(state.profile.avatar)
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    isSyncingRemoteData = false,
                    remoteDataError = error.message ?: "Unable to sync profile data."
                )
            }
        }
    }

    fun updateSelfDescription(value: String) {
        uiState = uiState.copy(selfDescription = value, profile = uiState.profile?.copy(bio = value))
    }

    fun selectGender(value: String) {
        uiState = uiState.copy(
            selectedGender = value,
            profile = uiState.profile?.copy(gender = value)
        )
    }

    fun updateDreamDescription(value: String) {
        uiState = uiState.copy(dreamDescription = value, profile = uiState.profile?.copy(dreamDescription = value))
    }

    fun toggleSelfTag(label: String) {
        val updated = uiState.selfTags.map { if (it.label == label) it.copy(selected = !it.selected) else it }
        uiState = uiState.copy(
            selfTags = updated,
            profile = uiState.profile?.copy(selfTags = updated.filter { it.selected }.map { it.label })
        )
    }

    fun toggleDreamTag(label: String) {
        val updated = uiState.dreamTags.map { if (it.label == label) it.copy(selected = !it.selected) else it }
        uiState = uiState.copy(
            dreamTags = updated,
            profile = uiState.profile?.copy(dreamTags = updated.filter { it.selected }.map { it.label })
        )
    }

    fun selectAvatar(id: String) {
        val avatar = uiState.avatarVariants.firstOrNull { it.id == id }
        uiState = uiState.copy(
            selectedAvatarId = id,
            profile = avatar?.let { uiState.profile?.copy(avatar = it) } ?: uiState.profile
        )
    }

    fun generateAvatarVariants() {
        if (uiState.isGeneratingAvatars) return
        Log.d(TAG, "generateAvatarVariants:start selfDescriptionLength=${uiState.selfDescription.length}")
        uiState = uiState.copy(
            isGeneratingAvatars = true,
            avatarGenerationError = null,
            avatarVariants = emptyList(),
            selectedAvatarId = null
        )
        viewModelScope.launch {
            runCatching {
                avatarGateway.generateVariants(
                    selfDescription = uiState.selfDescription,
                    selfTags = uiState.selfTags.filter { it.selected }.map { it.label },
                    selectedGender = uiState.selectedGender
                )
            }
                .onSuccess { result ->
                    Log.d(TAG, "generateAvatarVariants:success variants=${result.variants.size} previewUrl=${result.variants.firstOrNull()?.previewUrl} ipfsUrl=${result.variants.firstOrNull()?.ipfsUrl}")
                    val fallback = repository.avatarVariants()
                    val refreshed = result.variants.mapIndexed { index, asset ->
                        AvatarVariant(
                            id = ('a'.code + index).toChar().toString(),
                            title = if (index == 0) "Generated Avatar" else "Avatar ${index + 1}",
                            palette = fallback[index % fallback.size].palette,
                            previewUrl = asset.previewUrl,
                            ipfsUrl = asset.ipfsUrl
                        )
                    }
                    val selected = refreshed.firstOrNull()
                    uiState = uiState.copy(
                        avatarVariants = refreshed.ifEmpty { uiState.avatarVariants },
                        selectedAvatarId = selected?.id ?: uiState.selectedAvatarId,
                        isGeneratingAvatars = false,
                        avatarGenerationError = null,
                        profile = selected?.let { uiState.profile?.copy(avatar = it) } ?: uiState.profile
                    )
                    Log.d(TAG, "generateAvatarVariants:stateUpdated selectedAvatarId=${uiState.selectedAvatarId} profilePreviewUrl=${uiState.profile?.avatar?.previewUrl}")
                }
                .onFailure { error ->
                    Log.e(TAG, "generateAvatarVariants:failure ${error.message}", error)
                    uiState = uiState.copy(
                        isGeneratingAvatars = false,
                        avatarGenerationError = error.message ?: "Unable to generate avatar."
                    )
                }
        }
    }

    fun regenerateAvatars() {
        if (uiState.regenCredits == 0) return
        uiState = uiState.copy(regenCredits = uiState.regenCredits - 1)
        generateAvatarVariants()
    }

    fun setMintStep(index: Int) {
        uiState = uiState.copy(mintStep = index)
    }

    fun saveProfile(onDone: (() -> Unit)? = null) {
        val uid = uiState.authUid ?: return
        val profile = uiState.profile
        viewModelScope.launch {
            runCatching {
                remoteRepository.saveProfile(
                    uid = uid,
                    gender = uiState.selectedGender,
                    selfDescription = uiState.selfDescription,
                    dreamDescription = uiState.dreamDescription,
                    selfTags = uiState.selfTags,
                    dreamTags = uiState.dreamTags,
                    avatar = profile?.avatar
                )
            }.onSuccess {
                runCatching { refreshMatchFeedGateway.refresh() }
                onDone?.invoke()
            }.onFailure { error ->
                uiState = uiState.copy(remoteDataError = error.message ?: "Unable to save profile.")
            }
        }
    }

    fun mintProfile(onComplete: () -> Unit) {
        val profile = uiState.profile ?: return
        if (uiState.isMintingProfile) return
        uiState = uiState.copy(isMintingProfile = true, mintError = null)
        viewModelScope.launch {
            runCatching { mintGateway.mint(profile) }
                .onSuccess { result ->
                    val updatedProfile = profile.copy(
                        tokenId = result.tokenId,
                        tier = result.tier
                    )
                    uiState = uiState.copy(
                        isMintingProfile = false,
                        mintError = null,
                        mintTxHash = result.txHash,
                        mintMode = result.mintMode,
                        mintWalletAddress = result.walletAddress,
                        mintMetadataIpfsUrl = result.metadataIpfsUrl,
                        mintExternalUrl = result.externalMintUrl,
                        mintExternalPlatform = result.externalMintPlatform,
                        mintExternalNetwork = result.externalMintNetwork,
                        mintExternalGatewayUrl = result.externalMintGatewayUrl,
                        mintPreparedTransactionBase64 = result.preparedMintTransactionBase64,
                        mintPreparedMintAddress = result.preparedMintAddress,
                        mintPreparedMinContextSlot = result.preparedMintMinContextSlot,
                        mintPreparedRpcUrl = result.preparedMintRpcUrl,
                        profile = updatedProfile
                    )
                    runCatching { refreshMatchFeedGateway.refresh() }
                    saveProfile()
                    onComplete()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isMintingProfile = false,
                        isMintFlowRunning = false,
                        mintError = error.message ?: "Unable to mint profile."
                    )
                }
        }
    }

    suspend fun prepareMintProfileForFlow() {
        val profile = uiState.profile ?: error("Profile is missing for mint.")
        if (uiState.isMintingProfile) return
        uiState = uiState.copy(isMintingProfile = true, mintError = null)
        val result = mintGateway.mint(profile)
        val updatedProfile = profile.copy(
            tokenId = result.tokenId,
            tier = result.tier
        )
        uiState = uiState.copy(
            isMintingProfile = false,
            mintError = null,
            mintTxHash = result.txHash,
            mintMode = result.mintMode,
            mintWalletAddress = result.walletAddress,
            mintMetadataIpfsUrl = result.metadataIpfsUrl,
            mintExternalUrl = result.externalMintUrl,
            mintExternalPlatform = result.externalMintPlatform,
            mintExternalNetwork = result.externalMintNetwork,
            mintExternalGatewayUrl = result.externalMintGatewayUrl,
            mintPreparedTransactionBase64 = result.preparedMintTransactionBase64,
            mintPreparedMintAddress = result.preparedMintAddress,
            mintPreparedMinContextSlot = result.preparedMintMinContextSlot,
            mintPreparedRpcUrl = result.preparedMintRpcUrl,
            profile = updatedProfile
        )
        runCatching { refreshMatchFeedGateway.refresh() }
        val uid = uiState.authUid
        if (uid != null) {
            runCatching {
                remoteRepository.saveProfile(
                    uid = uid,
                    gender = uiState.selectedGender,
                    selfDescription = uiState.selfDescription,
                    dreamDescription = uiState.dreamDescription,
                    selfTags = uiState.selfTags,
                    dreamTags = uiState.dreamTags,
                    avatar = updatedProfile.avatar
                )
            }
        }
    }

    fun beginMintFlow() {
        uiState = uiState.copy(
            isMintingProfile = false,
            isConfirmingMintSubmission = false,
            isMintFlowRunning = true,
            mintError = null,
            mintTxHash = null,
            mintMode = null,
            mintWalletAddress = null,
            mintMetadataIpfsUrl = null,
            mintExternalUrl = null,
            mintExternalPlatform = null,
            mintExternalNetwork = null,
            mintExternalGatewayUrl = null,
            mintPreparedTransactionBase64 = null,
            mintPreparedMintAddress = null,
            mintPreparedMinContextSlot = null,
            mintPreparedRpcUrl = null,
            walletConnectError = null
        )
    }

    fun confirmSubmittedMint(signature: String, onConfirmed: () -> Unit) {
        uiState = uiState.copy(
            mintTxHash = signature,
            mintMode = "solana_submitted",
            isConfirmingMintSubmission = true,
            mintError = null
        )

        viewModelScope.launch {
            runCatching {
                solanaRpcGateway.awaitConfirmedSignature(signature)
            }.onSuccess {
                uiState = uiState.copy(
                    isConfirmingMintSubmission = false,
                    isMintFlowRunning = false,
                    mintMode = "solana_confirmed",
                    mintError = null
                )
                onConfirmed()
            }.onFailure { error ->
                uiState = uiState.copy(
                    isConfirmingMintSubmission = false,
                    isMintFlowRunning = false,
                    mintError = error.message ?: "Transaction signature was returned, but mainnet confirmation did not complete."
                )
            }
        }
    }

    fun connectSolanaWallet(walletAddress: String, authToken: String? = null) {
        val trimmed = walletAddress.trim()
        uiState = uiState.copy(
            solanaWalletAddress = trimmed,
            solanaWalletAuthToken = authToken ?: uiState.solanaWalletAuthToken,
            walletConnectError = null,
            profile = uiState.profile?.copy(walletAddress = trimmed)
        )
    }

    fun walletConnectionFailed(message: String) {
        uiState = uiState.copy(isMintFlowRunning = false, walletConnectError = message)
    }

    fun mintFlowFailed(message: String) {
        Log.e(TAG, "mintFlowFailed: $message")
        uiState = uiState.copy(isMintingProfile = false, isMintFlowRunning = false, mintError = message)
    }

    fun markPreparedMintSubmitted(signature: String) {
        uiState = uiState.copy(
            mintTxHash = signature,
            mintMode = "solana_submitted",
            mintError = null
        )
    }

    fun openChat(chatId: String) {
        val uid = uiState.authUid ?: return
        uiState = uiState.copy(activeChatId = chatId)
        startChatMessagesObserver(uid, chatId)
        viewModelScope.launch {
            runCatching { remoteRepository.loadChatMessages(uid, chatId) }
                .onSuccess { messages ->
                    uiState = uiState.copy(activeChatMessages = messages)
                }
                .onFailure { error ->
                    uiState = uiState.copy(remoteDataError = error.message ?: "Unable to load messages.")
                }
        }
    }

    fun updateChatDraft(value: String) {
        uiState = uiState.copy(chatDraft = value)
    }

    fun signOut(onDone: () -> Unit) {
        clearRealtimeObservers()
        authRepository.signOut()
        uiState = defaultUiState()
        onDone()
    }

    fun preferredPostAuthRoute(): String {
        if (uiState.authUid.isNullOrBlank()) return "auth"
        if (uiState.isSyncingRemoteData) return "loading"
        return if (uiState.hasMintedProfile) "feed" else "self"
    }

    fun prepareReportForCurrentFeed() {
        val current = uiState.feed.getOrNull(uiState.currentFeedIndex) ?: return
        uiState = uiState.copy(
            reportTargetId = current.id,
            reportTargetName = current.name,
            reportReason = "",
            reportError = null
        )
    }

    fun prepareReportForChat(chatId: String) {
        val chat = uiState.chats.firstOrNull { it.id == chatId }
        uiState = uiState.copy(
            reportTargetId = chatId,
            reportTargetName = chat?.name ?: uiState.reportTargetName,
            reportReason = "",
            reportError = null
        )
    }

    fun prepareReportFromContext() {
        when {
            uiState.activeChatId != null -> prepareReportForChat(uiState.activeChatId!!)
            uiState.feed.getOrNull(uiState.currentFeedIndex) != null -> prepareReportForCurrentFeed()
            else -> {
                uiState = uiState.copy(
                    reportTargetId = null,
                    reportTargetName = null,
                    reportReason = "",
                    reportError = "No active user selected to report."
                )
            }
        }
    }

    fun updateReportReason(value: String) {
        uiState = uiState.copy(reportReason = value)
    }

    fun passCurrentFeed() {
        val current = uiState.feed.getOrNull(uiState.currentFeedIndex) ?: return
        uiState = uiState.copy(
            remoteDataError = null,
            usagePaywall = null,
            isPassingFeed = true,
            feed = emptyList(),
            currentFeedIndex = 0
        )
        viewModelScope.launch {
            runCatching {
                recordFeedActionGateway.save(current.id, "pass")
                refreshMatchFeedGateway.refresh()
            }
                .onSuccess {
                    uiState = uiState.copy(
                        feed = it,
                        currentFeedIndex = 0,
                        isPassingFeed = false
                    )
                }
                .onFailure { error ->
                    when (error) {
                        is UsagePaymentRequiredException -> {
                            uiState = uiState.copy(
                                usagePaywall = error.paywall,
                                remoteDataError = error.message,
                                isPassingFeed = false
                            )
                        }
                        else -> {
                            uiState = uiState.copy(
                                remoteDataError = error.message ?: "Unable to pass this profile.",
                                isPassingFeed = false
                            )
                        }
                    }
                }
        }
    }

    fun likeCurrentFeed(superLike: Boolean = false, onMatched: ((String) -> Unit)? = null) {
        val uid = uiState.authUid ?: return
        val current = uiState.feed.getOrNull(uiState.currentFeedIndex) ?: return
        uiState = uiState.copy(remoteDataError = null)
        viewModelScope.launch {
            runCatching {
                recordFeedActionGateway.save(current.id, if (superLike) "super_like" else "like")
                remoteRepository.createMatchChat(
                    uid = uid,
                    profile = current,
                    isSuperLike = superLike
                )
            }.onSuccess { preview ->
                val updatedFeed = uiState.feed
                uiState = uiState.copy(
                    chats = listOf(preview) + uiState.chats.filterNot { it.id == preview.id },
                    feed = updatedFeed,
                    latestMatchedChatId = preview.id,
                    currentFeedIndex = if (updatedFeed.isEmpty()) 0 else minOf(uiState.currentFeedIndex, updatedFeed.lastIndex)
                )
                onMatched?.invoke(preview.id)
            }.onFailure { error ->
                Log.e(TAG, "likeCurrentFeed failed", error)
                uiState = uiState.copy(remoteDataError = error.message ?: "Unable to create match.")
            }
        }
    }

    fun sendChatMessage() {
        val uid = uiState.authUid ?: return
        val chatId = uiState.activeChatId ?: return
        val message = uiState.chatDraft.trim()
        if (message.isEmpty()) return
        val senderName = uiState.profile?.name ?: "You"
        viewModelScope.launch {
            runCatching {
                sendChatMessageGateway.send(
                    chatId = chatId,
                    senderName = senderName,
                    message = message
                )
                remoteRepository.loadChatMessages(uid, chatId)
            }.onSuccess { messages ->
                val updatedChats = uiState.chats.map {
                    if (it.id == chatId) it.copy(lastMessage = messages.lastOrNull()?.content ?: message, timestamp = "now") else it
                }
                uiState = uiState.copy(
                    chatDraft = "",
                    activeChatMessages = messages,
                    chats = updatedChats
                )
            }.onFailure { error ->
                when (error) {
                    is UsagePaymentRequiredException -> {
                        uiState = uiState.copy(
                            usagePaywall = error.paywall,
                            remoteDataError = error.message ?: "Payment required to continue."
                        )
                    }
                    else -> {
                        uiState = uiState.copy(remoteDataError = error.message ?: "Unable to send message.")
                    }
                }
                }
        }
    }

    fun dismissUsagePaywall() {
        uiState = uiState.copy(usagePaywall = null)
    }

    fun confirmUsagePayment() {
        val paywall = uiState.usagePaywall ?: return
        uiState = uiState.copy(isCheckingUsagePayment = true, remoteDataError = null)
        viewModelScope.launch {
            runCatching { checkUsagePaymentGateway.check(paywall.reference) }
                .onSuccess { result ->
                    uiState = uiState.copy(
                        isCheckingUsagePayment = false,
                        usagePaywall = if (result.unlocked) null else uiState.usagePaywall,
                        remoteDataError = if (result.unlocked) "Payment confirmed. You can continue now." else "Payment not found yet."
                    )
                    if (result.unlocked) {
                        runCatching { refreshMatchFeedGateway.refresh() }
                            .onSuccess { refreshed ->
                                uiState = uiState.copy(feed = refreshed, currentFeedIndex = 0)
                            }
                    }
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isCheckingUsagePayment = false,
                        remoteDataError = error.message ?: "Unable to verify payment yet."
                    )
                }
        }
    }

    fun consumeLatestMatchedChatId(): String? {
        val value = uiState.latestMatchedChatId
        if (value != null) {
            uiState = uiState.copy(latestMatchedChatId = null)
        }
        return value
    }

    fun deleteActiveChat(onDone: () -> Unit) {
        val chatId = uiState.activeChatId ?: return
        if (uiState.isDeletingChat) return
        uiState = uiState.copy(isDeletingChat = true, remoteDataError = null)
        viewModelScope.launch {
            runCatching { deleteChatThreadGateway.delete(chatId) }
                .onSuccess {
                    if (uiState.activeChatId == chatId) {
                        chatMessagesListener?.remove()
                        chatMessagesListener = null
                    }
                    uiState = uiState.copy(
                        isDeletingChat = false,
                        chats = uiState.chats.filterNot { it.id == chatId },
                        activeChatId = null,
                        activeChatMessages = emptyList(),
                        chatDraft = ""
                    )
                    onDone()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isDeletingChat = false,
                        remoteDataError = error.message ?: "Unable to delete this chat."
                    )
                }
        }
    }

    fun submitReportAndBlock(onDone: () -> Unit) {
        val targetId = uiState.reportTargetId ?: run {
            uiState = uiState.copy(reportError = "Choose a user to report first.")
            return
        }
        val reason = uiState.reportReason.trim()
        if (reason.length < 4) {
            uiState = uiState.copy(reportError = "Add a short reason before submitting.")
            return
        }
        if (uiState.isReporting) return

        uiState = uiState.copy(isReporting = true, reportError = null)
        viewModelScope.launch {
            runCatching { reportAndBlockGateway.submit(targetId = targetId, reason = reason) }
                .onSuccess {
                    val updatedFeed = uiState.feed.filterNot { profile -> profile.id == targetId }
                    val updatedChats = uiState.chats.filterNot { chat -> chat.id == targetId }
                    val updatedIndex = if (updatedFeed.isEmpty()) {
                        0
                    } else {
                        minOf(uiState.currentFeedIndex, updatedFeed.lastIndex)
                    }
                    val clearedActiveChatId = if (uiState.activeChatId == targetId) null else uiState.activeChatId
                    val clearedMessages = if (uiState.activeChatId == targetId) emptyList() else uiState.activeChatMessages
                    if (uiState.activeChatId == targetId) {
                        chatMessagesListener?.remove()
                        chatMessagesListener = null
                    }
                    uiState = uiState.copy(
                        isReporting = false,
                        reportError = null,
                        reportTargetId = null,
                        reportTargetName = null,
                        reportReason = "",
                        feed = updatedFeed,
                        chats = updatedChats,
                        currentFeedIndex = updatedIndex,
                        activeChatId = clearedActiveChatId,
                        activeChatMessages = clearedMessages
                    )
                    onDone()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isReporting = false,
                        reportError = error.message ?: "Unable to report and block this user."
                    )
                }
        }
    }

    fun deleteMyAccount(onDone: () -> Unit) {
        if (uiState.isDeletingAccount) return
        uiState = uiState.copy(isDeletingAccount = true, remoteDataError = null)
        viewModelScope.launch {
            runCatching { deleteMyAccountGateway.delete() }
                .onSuccess {
                    clearRealtimeObservers()
                    authRepository.signOut()
                    uiState = defaultUiState()
                    onDone()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isDeletingAccount = false,
                        remoteDataError = error.message ?: "Unable to delete account."
                    )
                }
        }
    }

    private fun defaultUiState(authUid: String? = null): SoulMintUiState {
        val avatars = repository.avatarVariants()
        return SoulMintUiState(
            selfDescription = "I am a warm, creative person with bookshop energy, silver jewelry, and a calm voice that gets brighter when I talk about art.",
            dreamDescription = "I want someone witty, adventurous, and emotionally fluent who likes rituals, honesty, and tiny midnight adventures.",
            selfTags = repository.suggestedSelfTags(),
            dreamTags = repository.suggestedDreamTags(),
            avatarVariants = avatars,
            selectedAvatarId = avatars.first().id,
            avatarPipeline = repository.avatarPipeline(),
            feed = repository.feedProfiles(),
            chats = repository.chats(),
            profile = repository.myProfile(),
            authUid = authUid,
            selectedGender = repository.myProfile().gender
        )
    }

    private fun startChatsObserver(uid: String) {
        chatsListener?.remove()
        chatsListener = remoteRepository.observeChats(
            uid = uid,
            onUpdate = { chats ->
                uiState = uiState.copy(chats = chats)
            },
            onError = { error ->
                uiState = uiState.copy(remoteDataError = error.message ?: "Unable to sync chats.")
            }
        )
    }

    private fun startChatMessagesObserver(uid: String, chatId: String) {
        chatMessagesListener?.remove()
        chatMessagesListener = remoteRepository.observeChatMessages(
            uid = uid,
            chatId = chatId,
            onUpdate = { messages ->
                uiState = uiState.copy(activeChatMessages = messages)
            },
            onError = { error ->
                uiState = uiState.copy(remoteDataError = error.message ?: "Unable to sync messages.")
            }
        )
    }

    private fun clearRealtimeObservers() {
        chatsListener?.remove()
        chatsListener = null
        chatMessagesListener?.remove()
        chatMessagesListener = null
    }

    override fun onCleared() {
        clearRealtimeObservers()
        super.onCleared()
    }
}
