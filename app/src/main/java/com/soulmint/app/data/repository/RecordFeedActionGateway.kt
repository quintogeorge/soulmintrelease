package com.soulmint.data.repository

import android.util.Log
import retrofit2.HttpException

class RecordFeedActionGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun save(targetId: String, action: String): Boolean {
        val jwt = tokenProvider.requireIdToken()
        return try {
            val response = api.recordFeedAction(
                authorization = "Bearer $jwt",
                body = RecordFeedActionBody(
                    targetId = targetId,
                    action = action
                )
            )
            Log.d("SoulMintDebug", "recordFeedAction success targetId=$targetId action=$action saved=${response.saved}")
            response.saved
        } catch (error: HttpException) {
            error.toUsagePaymentExceptionOrNull()?.let { throw it }
            val body = error.response()?.errorBody()?.string().orEmpty()
            Log.e(
                "SoulMintDebug",
                "recordFeedAction http error code=${error.code()} targetId=$targetId action=$action body=$body",
                error
            )
            throw IllegalStateException(
                if (body.isNotBlank()) "Feed action failed (${error.code()}): $body" else "Feed action failed (${error.code()}).",
                error
            )
        }
    }
}
