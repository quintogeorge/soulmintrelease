package com.soulmint.data.repository

import android.app.Activity

class WalletSignInGateway(
    private val api: FirebaseFunctionsAvatarApi = FirebaseFunctionsAvatarApi.create(),
    private val connector: SolanaWalletAdapterConnector = SolanaWalletAdapterConnector(),
    private val authRepository: FirebaseAuthRepository = FirebaseAuthRepository()
) {
    suspend fun signIn(activity: Activity): AuthSession {
        val authorization = connector.authorizeOwnerWallet(activity)
        val challenge = api.requestWalletSignInChallenge(
            WalletAuthChallengeBody(walletAddress = authorization.ownerWalletAddress)
        )
        val signed = connector.signInWithChallenge(activity, challenge.message)
        val completed = api.completeWalletSignIn(
            CompleteWalletSignInBody(
                walletAddress = signed.authorization.ownerWalletAddress,
                nonce = challenge.nonce,
                message = signed.signedMessage,
                signatureBase64 = signed.signatureBase64
            )
        )
        return authRepository.signInWithCustomToken(completed.customToken)
    }
}
