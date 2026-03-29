package com.soulmint.data.repository

class FakeFirebaseAvatarPipeline : AvatarPipelineDataSource {
    override suspend fun generateVariants(request: AvatarGenerationRequest): AvatarGenerationResult {
        return AvatarGenerationResult(
            variants = listOf(
                AvatarVariantAsset("https://preview.soulmint.app/avatar/generated", "ipfs://soulmint-avatar-generated")
            )
        )
    }
}
