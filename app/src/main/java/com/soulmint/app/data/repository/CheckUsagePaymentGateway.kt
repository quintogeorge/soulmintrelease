package com.soulmint.data.repository

class CheckUsagePaymentGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun check(reference: String): CheckUsagePaymentResponse {
        val jwt = tokenProvider.requireIdToken()
        return api.checkUsagePayment(
            authorization = "Bearer $jwt",
            body = CheckUsagePaymentBody(reference = reference)
        )
    }
}
