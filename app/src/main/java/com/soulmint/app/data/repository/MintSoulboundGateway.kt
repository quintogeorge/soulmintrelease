package com.soulmint.data.repository

import android.util.Log
import com.soulmint.data.model.ProfileCard
import retrofit2.HttpException

data class MintSoulboundResult(
    val tokenId: Int,
    val tier: String,
    val txHash: String,
    val mintMode: String?,
    val walletAddress: String?,
    val metadataIpfsUrl: String?,
    val externalMintUrl: String?,
    val externalMintPlatform: String?,
    val externalMintNetwork: String?,
    val externalMintGatewayUrl: String?,
    val preparedMintTransactionBase64: String?,
    val preparedMintAddress: String?,
    val preparedMintMinContextSlot: Int?,
    val preparedMintRpcUrl: String?
)

class MintSoulboundGateway(
    private val tokenProvider: FirebaseAuthTokenProvider = FirebaseAuthTokenProvider(),
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create()
) {
    private companion object {
        const val TAG = "SoulMintDebug"
    }

    suspend fun mint(profile: ProfileCard): MintSoulboundResult {
        val jwt = tokenProvider.requireIdToken()
        val ownerWalletAddress = profile.walletAddress ?: error("A Solana wallet must be connected before preparing mint.")
        val avatarPreviewUrl = profile.avatar.previewUrl?.trim().orEmpty()
        val avatarIpfsUrl = profile.avatar.ipfsUrl?.trim().orEmpty()
        require(avatarPreviewUrl.isNotBlank()) {
            "Minting requires a generated avatar preview URL."
        }
        require(avatarIpfsUrl.isNotBlank()) {
            "Minting requires a generated avatar IPFS URL."
        }

        Log.d(
            TAG,
            "mint:request wallet=$ownerWalletAddress previewUrl=$avatarPreviewUrl ipfsUrl=$avatarIpfsUrl"
        )

        val response = try {
            api.mintSoulboundProfile(
                authorization = "Bearer $jwt",
                body = MintSoulboundProfileBody(
                    name = profile.name,
                    bio = profile.bio,
                    dreamDescription = profile.dreamDescription,
                    selfTags = profile.selfTags,
                    dreamTags = profile.dreamTags,
                    avatarPreviewUrl = avatarPreviewUrl,
                    avatarIpfsUrl = avatarIpfsUrl,
                    ownerWalletAddress = ownerWalletAddress
                )
            )
        } catch (error: HttpException) {
            val details = error.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            throw IllegalStateException(
                "Mint request failed (${error.code()})${details?.let { ": $it" } ?: ""}",
                error
            )
        }
        return MintSoulboundResult(
            tokenId = response.tokenId,
            tier = response.tier,
            txHash = response.txHash,
            mintMode = response.mintMode,
            walletAddress = response.walletAddress,
            metadataIpfsUrl = response.metadataIpfsUrl,
            externalMintUrl = response.externalMintUrl,
            externalMintPlatform = response.externalMint?.platform,
            externalMintNetwork = response.externalMint?.network,
            externalMintGatewayUrl = response.externalMint?.metadataGatewayUrl,
            preparedMintTransactionBase64 = response.externalMint?.preparedMint?.transactionBase64,
            preparedMintAddress = response.externalMint?.preparedMint?.mintAddress,
            preparedMintMinContextSlot = response.externalMint?.preparedMint?.minContextSlot,
            preparedMintRpcUrl = response.externalMint?.preparedMint?.rpcUrl
        )
    }
}
