package com.soulmint.data.repository

import retrofit2.HttpException

class SendChatMessageGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    suspend fun send(chatId: String, senderName: String, message: String): String {
        val jwt = tokenProvider.requireIdToken()
        return try {
            val response = api.sendChatMessage(
                authorization = "Bearer $jwt",
                body = SendChatMessageBody(
                    chatId = chatId,
                    message = message,
                    senderName = senderName
                )
            )
            response.reply
        } catch (error: HttpException) {
            error.toUsagePaymentExceptionOrNull()?.let { throw it }
            throw error
        }
    }
}
