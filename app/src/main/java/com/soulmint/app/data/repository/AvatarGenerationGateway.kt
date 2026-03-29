package com.soulmint.data.repository

import android.util.Log
import java.io.IOException
import retrofit2.HttpException

class AvatarGenerationGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val pipelineDataSource: AvatarPipelineDataSource = CloudFunctionsAvatarPipeline()
) {
    private companion object {
        const val TAG = "SoulMintDebug"
    }

    suspend fun generateVariants(
        selfDescription: String,
        selfTags: List<String>,
        selectedGender: String?
    ): AvatarGenerationResult {
        return try {
            val jwt = tokenProvider.requireIdToken(forceRefresh = true)
            pipelineDataSource.generateVariants(
                AvatarGenerationRequest(selfDescription, selfTags, selectedGender, jwt)
            )
        } catch (error: Throwable) {
            Log.e(TAG, "generateVariants:gatewayFailure", error)
            throw IllegalStateException(error.toAvatarMessage(), error)
        }
    }
}

private fun Throwable.toAvatarMessage(): String {
    return when (this) {
        is HttpException -> {
            val details = response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            "Avatar generation failed (${code()})${details?.let { ": $it" } ?: ""}"
        }
        is IOException -> "Avatar generation could not reach Firebase Cloud Functions. Check the network and try again."
        else -> message ?: "Avatar generation failed before a portrait was returned."
    }
}
