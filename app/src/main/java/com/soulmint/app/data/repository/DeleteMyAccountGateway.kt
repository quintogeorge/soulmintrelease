package com.soulmint.data.repository

class DeleteMyAccountGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun delete(): Boolean {
        val jwt = tokenProvider.requireIdToken()
        return api.deleteMyAccount(
            authorization = "Bearer $jwt"
        ).deleted
    }
}
