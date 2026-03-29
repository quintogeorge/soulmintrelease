package com.soulmint.data.repository

class DeleteChatThreadGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun delete(chatId: String): DeleteChatThreadResponse {
        val jwt = tokenProvider.requireIdToken()
        return api.deleteChatThread(
            authorization = "Bearer $jwt",
            body = DeleteChatThreadBody(chatId = chatId)
        )
    }
}
