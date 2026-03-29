package com.soulmint.data.repository

import androidx.compose.ui.graphics.Color
import com.soulmint.data.model.AvatarPipelineStep
import com.soulmint.data.model.AvatarVariant
import com.soulmint.data.model.ChatPreview
import com.soulmint.data.model.MatchProfile
import com.soulmint.data.model.MintProgressStep
import com.soulmint.data.model.ProfileCard
import com.soulmint.data.model.TraitTag

class SoulMintRepository {
    fun suggestedSelfTags() = listOf(
        TraitTag("warm"),
        TraitTag("creative"),
        TraitTag("bookish"),
        TraitTag("soft-spoken"),
        TraitTag("curious"),
        TraitTag("romantic"),
        TraitTag("introspective"),
        TraitTag("playful"),
        TraitTag("cinematic"),
        TraitTag("ambitious"),
        TraitTag("grounded"),
        TraitTag("fashion-forward")
    )

    fun suggestedDreamTags() = listOf(
        TraitTag("witty"),
        TraitTag("adventurous"),
        TraitTag("empathetic"),
        TraitTag("grounded"),
        TraitTag("playful"),
        TraitTag("romantic"),
        TraitTag("curious"),
        TraitTag("communicative"),
        TraitTag("gentle"),
        TraitTag("confident"),
        TraitTag("bookish"),
        TraitTag("artistic")
    )

    fun avatarVariants() = listOf(
        AvatarVariant("a", "Variant A", listOf(Color(0xFF3F285C), Color(0xFFE05C8A))),
        AvatarVariant("b", "Variant B", listOf(Color(0xFF19233E), Color(0xFFA67BDB))),
        AvatarVariant("c", "Variant C", listOf(Color(0xFF4D2A3A), Color(0xFFD4A843))),
        AvatarVariant("d", "Variant D", listOf(Color(0xFF1F3840), Color(0xFF7B5EA7)))
    )

    fun avatarPipeline() = listOf(
        AvatarPipelineStep(
            "Android app sends text + Firebase JWT",
            "Self-description is posted to Cloud Functions with the signed-in user's Firebase Auth token."
        ),
        AvatarPipelineStep(
            "Cloud Functions builds the prompt",
            "Secrets stay server-side while the backend calls Stability and moderation/upload hooks."
        ),
        AvatarPipelineStep(
            "Moderation and IPFS upload",
            "The approved portrait can be uploaded to Pinata and returned with a preview URL plus an IPFS URL."
        ),
        AvatarPipelineStep(
            "One portrait returns to the app",
            "The app shows a single generated avatar and keeps its IPFS URL for minting."
        )
    )

    fun mintSteps(activeIndex: Int) = listOf(
        "Uploading avatar and metadata",
        "Connecting wallet",
        "Preparing Solana mint",
        "Confirming on Solana mainnet",
        "NFT ready"
    ).mapIndexed { index, title ->
        MintProgressStep(title, completed = index < activeIndex, active = index == activeIndex)
    }

    fun feedProfiles() = emptyList<MatchProfile>()

    fun chats() = emptyList<ChatPreview>()

    fun aiFallbackProfile(selectedDreamTags: List<String>, selectedSelfTags: List<String>) = MatchProfile(
        id = "ai:companion",
        name = "Astra",
        tier = "AI Companion",
        tokenId = 0,
        selfTags = if (selectedDreamTags.isNotEmpty()) selectedDreamTags.take(4) else listOf("gentle", "curious", "witty"),
        dreamTags = if (selectedSelfTags.isNotEmpty()) selectedSelfTags.take(4) else listOf("warm", "creative", "grounded"),
        compatibility = 88,
        avatar = avatarVariants()[1]
    )

    fun myProfile() = ProfileCard(
        name = "You",
        tier = "Unminted",
        tokenId = 0,
        gender = null,
        selfTags = suggestedSelfTags().map { it.label },
        dreamTags = suggestedDreamTags().map { it.label },
        compatibility = 94,
        avatar = avatarVariants().first(),
        bio = "A warm, creative night owl with bookshop energy, silver jewelry, and a romantic sci-fi streak.",
        dreamDescription = "Someone witty, emotionally fluent, and adventurous enough to turn ordinary nights into little rituals."
    )
}
