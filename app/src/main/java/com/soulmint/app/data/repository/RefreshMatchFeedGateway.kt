package com.soulmint.data.repository

import androidx.compose.ui.graphics.Color
import com.soulmint.data.model.AvatarVariant
import com.soulmint.data.model.MatchProfile

class RefreshMatchFeedGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun refresh(): List<MatchProfile> {
        val jwt = tokenProvider.requireIdToken()
        val response = api.refreshMatchFeed(
            authorization = "Bearer $jwt"
        )

        return response.profiles.map { profile ->
            MatchProfile(
                id = profile.id,
                name = profile.name,
                tier = profile.tier,
                tokenId = profile.tokenId,
                selfTags = profile.selfTags,
                dreamTags = profile.dreamTags,
                compatibility = profile.compatibility,
                avatar = AvatarVariant(
                    id = profile.id,
                    title = "${profile.name} Avatar",
                    palette = listOf(Color(0xFF3F285C), Color(0xFFE05C8A)),
                    previewUrl = profile.avatarPreviewUrl,
                    ipfsUrl = profile.avatarIpfsUrl
                ),
                aiPresentation = profile.aiPresentation,
                aiOpenerTone = profile.aiOpenerTone
            )
        }
    }
}
