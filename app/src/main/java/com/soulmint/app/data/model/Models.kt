package com.soulmint.data.model

import androidx.compose.ui.graphics.Color

data class TraitTag(
    val label: String,
    val selected: Boolean = true
)

data class AvatarVariant(
    val id: String,
    val title: String,
    val palette: List<Color>,
    val previewUrl: String? = null,
    val ipfsUrl: String? = null
)

data class MintProgressStep(
    val title: String,
    val completed: Boolean = false,
    val active: Boolean = false
)

data class AvatarPipelineStep(
    val title: String,
    val detail: String
)

data class MatchProfile(
    val id: String,
    val name: String,
    val tier: String,
    val tokenId: Int,
    val selfTags: List<String>,
    val dreamTags: List<String>,
    val compatibility: Int,
    val avatar: AvatarVariant,
    val aiPresentation: String? = null,
    val aiOpenerTone: String? = null
)

data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val avatar: AvatarVariant? = null
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val isMine: Boolean
)

data class UsagePaywall(
    val trigger: String,
    val reference: String,
    val recipientWalletAddress: String,
    val solAmount: Double,
    val skrAmount: Double,
    val skrTokenMint: String,
    val solanaPaySolUrl: String,
    val solanaPaySkrUrl: String
)

data class ProfileCard(
    val name: String,
    val tier: String,
    val tokenId: Int,
    val gender: String? = null,
    val selfTags: List<String>,
    val dreamTags: List<String>,
    val compatibility: Int,
    val avatar: AvatarVariant,
    val bio: String,
    val dreamDescription: String,
    val walletAddress: String? = null
)
