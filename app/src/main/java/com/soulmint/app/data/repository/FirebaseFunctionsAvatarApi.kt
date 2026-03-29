package com.soulmint.data.repository

import com.soulmint.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class GenerateAvatarVariantsBody(
    @Json(name = "selfDescription")
    val selfDescription: String,
    @Json(name = "selfTags")
    val selfTags: List<String> = emptyList(),
    @Json(name = "selectedGender")
    val selectedGender: String? = null
)

data class GeneratedAvatarDto(
    @Json(name = "previewUrl")
    val previewUrl: String,
    @Json(name = "ipfsUrl")
    val ipfsUrl: String
)

data class GenerateAvatarVariantsResponse(
    @Json(name = "variants")
    val variants: List<GeneratedAvatarDto>
)

data class MintSoulboundProfileBody(
    @Json(name = "name")
    val name: String,
    @Json(name = "bio")
    val bio: String,
    @Json(name = "dreamDescription")
    val dreamDescription: String,
    @Json(name = "selfTags")
    val selfTags: List<String>,
    @Json(name = "dreamTags")
    val dreamTags: List<String>,
    @Json(name = "avatarPreviewUrl")
    val avatarPreviewUrl: String?,
    @Json(name = "avatarIpfsUrl")
    val avatarIpfsUrl: String?,
    @Json(name = "ownerWalletAddress")
    val ownerWalletAddress: String
)

data class MintSoulboundProfileResponse(
    @Json(name = "tokenId")
    val tokenId: Int,
    @Json(name = "tier")
    val tier: String,
    @Json(name = "txHash")
    val txHash: String,
    @Json(name = "mintMode")
    val mintMode: String? = null,
    @Json(name = "walletAddress")
    val walletAddress: String? = null,
    @Json(name = "metadataIpfsUrl")
    val metadataIpfsUrl: String? = null,
    @Json(name = "externalMintUrl")
    val externalMintUrl: String? = null,
    @Json(name = "externalMint")
    val externalMint: ExternalMintHandoffDto? = null
)

data class ExternalMintHandoffDto(
    @Json(name = "platform")
    val platform: String,
    @Json(name = "action")
    val action: String,
    @Json(name = "network")
    val network: String,
    @Json(name = "ownerWalletAddress")
    val ownerWalletAddress: String,
    @Json(name = "metadataIpfsUrl")
    val metadataIpfsUrl: String,
    @Json(name = "metadataGatewayUrl")
    val metadataGatewayUrl: String,
    @Json(name = "preparedMint")
    val preparedMint: PreparedSolanaMintDto
)

data class PreparedSolanaMintDto(
    @Json(name = "transactionBase64")
    val transactionBase64: String,
    @Json(name = "mintAddress")
    val mintAddress: String,
    @Json(name = "ownerWalletAddress")
    val ownerWalletAddress: String,
    @Json(name = "minContextSlot")
    val minContextSlot: Int? = null,
    @Json(name = "rpcUrl")
    val rpcUrl: String
)

data class RefreshMatchFeedDto(
    @Json(name = "id")
    val id: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "tier")
    val tier: String,
    @Json(name = "tokenId")
    val tokenId: Int,
    @Json(name = "selfTags")
    val selfTags: List<String>,
    @Json(name = "dreamTags")
    val dreamTags: List<String>,
    @Json(name = "compatibility")
    val compatibility: Int,
    @Json(name = "avatarPreviewUrl")
    val avatarPreviewUrl: String,
    @Json(name = "avatarIpfsUrl")
    val avatarIpfsUrl: String,
    @Json(name = "aiPresentation")
    val aiPresentation: String? = null,
    @Json(name = "aiOpenerTone")
    val aiOpenerTone: String? = null
)

data class RefreshMatchFeedResponse(
    @Json(name = "profiles")
    val profiles: List<RefreshMatchFeedDto>
)

data class SendChatMessageBody(
    @Json(name = "chatId")
    val chatId: String,
    @Json(name = "message")
    val message: String,
    @Json(name = "senderName")
    val senderName: String
)

data class SendChatMessageResponse(
    @Json(name = "chatId")
    val chatId: String,
    @Json(name = "reply")
    val reply: String
)

data class UsagePaywallDto(
    @Json(name = "trigger")
    val trigger: String,
    @Json(name = "reference")
    val reference: String,
    @Json(name = "recipientWalletAddress")
    val recipientWalletAddress: String,
    @Json(name = "solAmount")
    val solAmount: Double,
    @Json(name = "skrAmount")
    val skrAmount: Double,
    @Json(name = "skrTokenMint")
    val skrTokenMint: String,
    @Json(name = "solanaPaySolUrl")
    val solanaPaySolUrl: String,
    @Json(name = "solanaPaySkrUrl")
    val solanaPaySkrUrl: String
)

data class UsagePaywallErrorDto(
    @Json(name = "error")
    val error: String,
    @Json(name = "code")
    val code: String? = null,
    @Json(name = "paywall")
    val paywall: UsagePaywallDto? = null
)

data class CheckUsagePaymentBody(
    @Json(name = "reference")
    val reference: String
)

data class CheckUsagePaymentResponse(
    @Json(name = "reference")
    val reference: String,
    @Json(name = "status")
    val status: String,
    @Json(name = "unlocked")
    val unlocked: Boolean,
    @Json(name = "usageUnlockCount")
    val usageUnlockCount: Int
)

data class ReportAndBlockUserBody(
    @Json(name = "targetId")
    val targetId: String,
    @Json(name = "reason")
    val reason: String
)

data class ReportAndBlockUserResponse(
    @Json(name = "targetId")
    val targetId: String,
    @Json(name = "blocked")
    val blocked: Boolean
)

data class DeleteMyAccountResponse(
    @Json(name = "deleted")
    val deleted: Boolean
)

data class RecordFeedActionBody(
    @Json(name = "targetId")
    val targetId: String,
    @Json(name = "action")
    val action: String
)

data class RecordFeedActionResponse(
    @Json(name = "targetId")
    val targetId: String,
    @Json(name = "action")
    val action: String,
    @Json(name = "saved")
    val saved: Boolean
)

data class DeleteChatThreadBody(
    @Json(name = "chatId")
    val chatId: String
)

data class DeleteChatThreadResponse(
    @Json(name = "chatId")
    val chatId: String,
    @Json(name = "deleted")
    val deleted: Boolean,
    @Json(name = "removedMessages")
    val removedMessages: Int
)

data class WalletAuthChallengeBody(
    @Json(name = "walletAddress")
    val walletAddress: String
)

data class WalletAuthChallengeResponse(
    @Json(name = "walletAddress")
    val walletAddress: String,
    @Json(name = "nonce")
    val nonce: String,
    @Json(name = "message")
    val message: String
)

data class CompleteWalletSignInBody(
    @Json(name = "walletAddress")
    val walletAddress: String,
    @Json(name = "nonce")
    val nonce: String,
    @Json(name = "message")
    val message: String,
    @Json(name = "signatureBase64")
    val signatureBase64: String
)

data class CompleteWalletSignInResponse(
    @Json(name = "customToken")
    val customToken: String,
    @Json(name = "uid")
    val uid: String,
    @Json(name = "walletAddress")
    val walletAddress: String
)

interface FirebaseFunctionsAvatarApi {
    @POST("requestWalletSignInChallenge")
    suspend fun requestWalletSignInChallenge(
        @Body body: WalletAuthChallengeBody
    ): WalletAuthChallengeResponse

    @POST("completeWalletSignIn")
    suspend fun completeWalletSignIn(
        @Body body: CompleteWalletSignInBody
    ): CompleteWalletSignInResponse

    @POST("generateAvatarVariants")
    suspend fun generateAvatarVariants(
        @Header("Authorization") authorization: String,
        @Body body: GenerateAvatarVariantsBody
    ): GenerateAvatarVariantsResponse

    @POST("mintSoulboundProfile")
    suspend fun mintSoulboundProfile(
        @Header("Authorization") authorization: String,
        @Body body: MintSoulboundProfileBody
    ): MintSoulboundProfileResponse

    @POST("refreshMatchFeed")
    suspend fun refreshMatchFeed(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, String> = emptyMap()
    ): RefreshMatchFeedResponse

    @POST("sendChatMessage")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String,
        @Body body: SendChatMessageBody
    ): SendChatMessageResponse

    @POST("checkUsagePayment")
    suspend fun checkUsagePayment(
        @Header("Authorization") authorization: String,
        @Body body: CheckUsagePaymentBody
    ): CheckUsagePaymentResponse

    @POST("reportAndBlockUser")
    suspend fun reportAndBlockUser(
        @Header("Authorization") authorization: String,
        @Body body: ReportAndBlockUserBody
    ): ReportAndBlockUserResponse

    @POST("deleteMyAccount")
    suspend fun deleteMyAccount(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, String> = emptyMap()
    ): DeleteMyAccountResponse

    @POST("recordFeedAction")
    suspend fun recordFeedAction(
        @Header("Authorization") authorization: String,
        @Body body: RecordFeedActionBody
    ): RecordFeedActionResponse

    @POST("deleteChatThread")
    suspend fun deleteChatThread(
        @Header("Authorization") authorization: String,
        @Body body: DeleteChatThreadBody
    ): DeleteChatThreadResponse

    companion object {
        fun create(baseUrl: String = BuildConfig.SOULMINT_FUNCTIONS_BASE_URL): FirebaseFunctionsAvatarApi {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(logger)
                .build()
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(FirebaseFunctionsAvatarApi::class.java)
        }
    }
}
