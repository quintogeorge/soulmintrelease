package com.soulmint.data.repository

class ReportAndBlockGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun submit(targetId: String, reason: String): ReportAndBlockUserResponse {
        val jwt = tokenProvider.requireIdToken()
        return api.reportAndBlockUser(
            authorization = "Bearer $jwt",
            body = ReportAndBlockUserBody(
                targetId = targetId,
                reason = reason
            )
        )
    }
}
