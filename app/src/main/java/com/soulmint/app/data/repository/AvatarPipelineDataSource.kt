package com.soulmint.data.repository

data class AvatarGenerationRequest(
    val selfDescription: String,
    val selfTags: List<String>,
    val selectedGender: String?,
    val firebaseJwt: String
)

data class AvatarVariantAsset(
    val previewUrl: String,
    val ipfsUrl: String
)

data class AvatarGenerationResult(
    val variants: List<AvatarVariantAsset>
)

interface AvatarPipelineDataSource {
    suspend fun generateVariants(request: AvatarGenerationRequest): AvatarGenerationResult
}
