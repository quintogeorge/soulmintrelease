package com.soulmint.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import com.soulmint.data.model.AvatarVariant
import com.soulmint.data.model.ChatMessage
import com.soulmint.data.model.ChatPreview
import com.soulmint.data.model.MatchProfile
import com.soulmint.data.model.ProfileCard
import com.soulmint.data.model.TraitTag

data class SoulMintRemoteState(
    val profile: ProfileCard,
    val selfTags: List<TraitTag>,
    val dreamTags: List<TraitTag>,
    val feed: List<MatchProfile>,
    val chats: List<ChatPreview>,
    val hasMintedProfile: Boolean
)

class FirestoreSoulMintRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun bootstrapUser(
        uid: String,
        defaultProfile: ProfileCard,
        defaultSelfTags: List<TraitTag>,
        defaultDreamTags: List<TraitTag>
    ) {
        val userRef = firestore.collection("users").document(uid)

        val userSnapshot = userRef.get().await()

        val batch = firestore.batch()

        if (!userSnapshot.exists()) {
            batch.set(
                userRef,
                mapOf(
                    "name" to defaultProfile.name,
                    "gender" to defaultProfile.gender,
                    "compatibility" to defaultProfile.compatibility,
                    "bio" to defaultProfile.bio,
                    "dreamDescription" to defaultProfile.dreamDescription,
                    "selfTags" to defaultProfile.selfTags,
                    "dreamTags" to defaultProfile.dreamTags,
                    "avatarTitle" to defaultProfile.avatar.title,
                    "avatarPreviewUrl" to defaultProfile.avatar.previewUrl,
                    "avatarIpfsUrl" to defaultProfile.avatar.ipfsUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        if (!userSnapshot.exists()) {
            batch.commit().await()
        }
    }

    suspend fun loadState(
        uid: String,
        fallbackProfile: ProfileCard,
        fallbackSelfTags: List<TraitTag>,
        fallbackDreamTags: List<TraitTag>
    ): SoulMintRemoteState {
        val userRef = firestore.collection("users").document(uid)
        val userSnapshot = userRef.get().await()
        val feedSnapshot = userRef.collection("feed").orderBy("compatibility", Query.Direction.DESCENDING).get().await()
        val chatSnapshot = userRef.collection("chats").orderBy("updatedAt", Query.Direction.DESCENDING).get().await()

        val profile = userSnapshot.toProfileCard(fallbackProfile)
        val selfTags = mergeTags(
            selectedLabels = profile.selfTags,
            suggestedTags = fallbackSelfTags
        )
        val dreamTags = mergeTags(
            selectedLabels = profile.dreamTags,
            suggestedTags = fallbackDreamTags
        )

        return SoulMintRemoteState(
            profile = profile,
            selfTags = if (selfTags.isEmpty()) fallbackSelfTags else selfTags,
            dreamTags = if (dreamTags.isEmpty()) fallbackDreamTags else dreamTags,
            feed = feedSnapshot.documents.map { it.toMatchProfile() },
            chats = chatSnapshot.documents.map { it.toChatPreview() },
            hasMintedProfile = userSnapshot.get("mintedAt") != null || !userSnapshot.getString("metadataIpfsUrl").isNullOrBlank()
        )
    }

    suspend fun saveProfile(
        uid: String,
        gender: String?,
        selfDescription: String,
        dreamDescription: String,
        selfTags: List<TraitTag>,
        dreamTags: List<TraitTag>,
        avatar: AvatarVariant?
    ) {
        firestore.collection("users").document(uid).set(
            mapOf(
                "gender" to gender,
                "bio" to selfDescription,
                "dreamDescription" to dreamDescription,
                "selfTags" to selfTags.filter { it.selected }.map { it.label },
                "dreamTags" to dreamTags.filter { it.selected }.map { it.label },
                "avatarTitle" to avatar?.title,
                "avatarPreviewUrl" to avatar?.previewUrl,
                "avatarIpfsUrl" to avatar?.ipfsUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun loadChatMessages(uid: String, chatId: String): List<ChatMessage> {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()

        val messages = snapshot.documents.map { document ->
            val senderUid = document.getString("senderUid")
            ChatMessage(
                id = document.id,
                senderName = document.getString("senderName").orEmpty(),
                content = document.getString("content").orEmpty(),
                timestamp = document.getString("timestamp").orEmpty(),
                isMine = senderUid == uid
            )
        }
        return if (messages.any { it.isMine }) messages else emptyList()
    }

    suspend fun sendMessage(uid: String, chatId: String, senderName: String, content: String) {
        val now = Timestamp.now()
        val chatRef = firestore.collection("users")
            .document(uid)
            .collection("chats")
            .document(chatId)

        val messagesRef = chatRef.collection("messages")
        messagesRef.add(
            mapOf(
                "senderUid" to uid,
                "senderName" to senderName,
                "content" to content,
                "timestamp" to "now",
                "createdAt" to now
            )
        ).await()

        chatRef.set(
            mapOf(
                "lastMessage" to content,
                "hasUserMessages" to true,
                "timestamp" to "now",
                "updatedAt" to now
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun createMatchChat(uid: String, profile: MatchProfile, isSuperLike: Boolean): ChatPreview {
        val now = Timestamp.now()
        val chatId = profile.id
        val chatRef = firestore.collection("users")
            .document(uid)
            .collection("chats")
            .document(chatId)
        val isAi = profile.id.startsWith("ai:") || profile.id.startsWith("match-")

        chatRef.set(
            mapOf(
                "name" to profile.name,
                "isAi" to isAi,
                "aiPresentation" to profile.aiPresentation,
                "aiOpenerTone" to profile.aiOpenerTone,
                "avatarTitle" to profile.avatar.title,
                "avatarPreviewUrl" to profile.avatar.previewUrl,
                "avatarIpfsUrl" to profile.avatar.ipfsUrl,
                "selfTags" to profile.selfTags,
                "dreamTags" to profile.dreamTags,
                "lastMessage" to "",
                "hasUserMessages" to false,
                "timestamp" to "new",
                "updatedAt" to now
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()

        return ChatPreview(
            id = profile.id,
            name = profile.name,
            lastMessage = "",
            timestamp = if (isSuperLike) "matched" else "new",
            avatar = profile.avatar
        )
    }

    fun observeChats(
        uid: String,
        onUpdate: (List<ChatPreview>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("users")
            .document(uid)
            .collection("chats")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> onError(error)
                    snapshot != null -> onUpdate(snapshot.documents.map { it.toChatPreview() })
                }
            }
    }

    fun observeChatMessages(
        uid: String,
        chatId: String,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("users")
            .document(uid)
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> onError(error)
                    snapshot != null -> onUpdate(
                        snapshot.documents.map { document ->
                            val senderUid = document.getString("senderUid")
                            ChatMessage(
                                id = document.id,
                                senderName = document.getString("senderName").orEmpty(),
                                content = document.getString("content").orEmpty(),
                                timestamp = document.getString("timestamp").orEmpty(),
                                isMine = senderUid == uid
                            )
                        }
                    )
                }
            }
    }

    private fun matchProfileToMap(profile: MatchProfile): Map<String, Any?> = mapOf(
        "name" to profile.name,
        "tier" to profile.tier,
        "tokenId" to profile.tokenId,
        "selfTags" to profile.selfTags,
        "dreamTags" to profile.dreamTags,
        "compatibility" to profile.compatibility,
        "avatarTitle" to profile.avatar.title,
        "avatarPreviewUrl" to profile.avatar.previewUrl,
        "avatarIpfsUrl" to profile.avatar.ipfsUrl,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toProfileCard(
        fallback: ProfileCard
    ): ProfileCard {
        if (!exists()) return fallback
        return ProfileCard(
            name = getString("name") ?: fallback.name,
            tier = getString("tier") ?: fallback.tier,
            tokenId = getLong("tokenId")?.toInt() ?: fallback.tokenId,
            gender = getString("gender") ?: fallback.gender,
            selfTags = getStringList("selfTags").ifEmpty { fallback.selfTags },
            dreamTags = getStringList("dreamTags").ifEmpty { fallback.dreamTags },
            compatibility = getLong("compatibility")?.toInt() ?: fallback.compatibility,
            avatar = AvatarVariant(
                id = "generated",
                title = getString("avatarTitle") ?: fallback.avatar.title,
                palette = fallback.avatar.palette,
                previewUrl = getString("avatarPreviewUrl") ?: fallback.avatar.previewUrl,
                ipfsUrl = getString("avatarIpfsUrl") ?: fallback.avatar.ipfsUrl
            ),
            bio = getString("bio") ?: fallback.bio,
            dreamDescription = getString("dreamDescription") ?: fallback.dreamDescription,
            walletAddress = getString("walletAddress") ?: fallback.walletAddress
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMatchProfile(): MatchProfile {
        return MatchProfile(
            id = id,
            name = getString("name").orEmpty(),
            tier = getString("tier").orEmpty(),
            tokenId = getLong("tokenId")?.toInt() ?: 0,
            selfTags = getStringList("selfTags"),
            dreamTags = getStringList("dreamTags"),
            compatibility = getLong("compatibility")?.toInt() ?: 0,
            avatar = AvatarVariant(
                id = id,
                title = getString("avatarTitle") ?: "Match Avatar",
                palette = listOf(androidx.compose.ui.graphics.Color(0xFF3F285C), androidx.compose.ui.graphics.Color(0xFFE05C8A)),
                previewUrl = getString("avatarPreviewUrl"),
                ipfsUrl = getString("avatarIpfsUrl")
            ),
            aiPresentation = getString("aiPresentation"),
            aiOpenerTone = getString("aiOpenerTone")
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatPreview(): ChatPreview {
        val hasUserMessages = getBoolean("hasUserMessages") ?: false
        return ChatPreview(
            id = id,
            name = getString("name").orEmpty(),
            lastMessage = if (hasUserMessages) getString("lastMessage").orEmpty() else "",
            timestamp = getString("timestamp").orEmpty(),
            avatar = AvatarVariant(
                id = id,
                title = getString("avatarTitle") ?: getString("name").orEmpty(),
                palette = listOf(androidx.compose.ui.graphics.Color(0xFF3F285C), androidx.compose.ui.graphics.Color(0xFFE05C8A)),
                previewUrl = getString("avatarPreviewUrl"),
                ipfsUrl = getString("avatarIpfsUrl")
            )
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getStringList(fieldName: String): List<String> {
        return (get(fieldName) as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    }

    private fun mergeTags(
        selectedLabels: List<String>,
        suggestedTags: List<TraitTag>
    ): List<TraitTag> {
        val selected = selectedLabels.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val merged = LinkedHashSet<String>()
        suggestedTags.forEach { merged += it.label }
        selected.forEach { merged += it }
        return merged.map { label ->
            TraitTag(label = label, selected = label in selected)
        }
    }
}
