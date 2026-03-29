package com.soulmint.data.repository

class CloudFunctionsAvatarPipeline(
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) : AvatarPipelineDataSource {
    override suspend fun generateVariants(request: AvatarGenerationRequest): AvatarGenerationResult {
        val response = api.generateAvatarVariants(
            authorization = "Bearer ${request.firebaseJwt}",
            body = GenerateAvatarVariantsBody(
                selfDescription = request.selfDescription,
                selfTags = request.selfTags,
                selectedGender = request.selectedGender
            )
        )
        require(response.variants.isNotEmpty()) {
            "Firebase Cloud Functions returned no portrait variants."
        }
        return AvatarGenerationResult(
            variants = response.variants.map {
                AvatarVariantAsset(it.previewUrl, it.ipfsUrl)
            }
        )
    }
}
