package com.soulmint.data.repository

import android.app.Activity
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

data class WalletAuthorizationSession(
    val ownerWalletAddress: String,
    val authToken: String?
)

data class SignedWalletMessage(
    val authorization: WalletAuthorizationSession,
    val signatureBase64: String,
    val signedMessage: String
)

class SolanaWalletAdapterConnector {
    suspend fun authorizeOwnerWallet(activity: Activity): WalletAuthorizationSession {
        return withAuthorizedWalletClient(activity, null) { _, authorization ->
            WalletAuthorizationSession(
                ownerWalletAddress = authorization.ownerWalletAddress,
                authToken = authorization.authToken
            )
        }
    }

    suspend fun executeAuthorizedMintFlow(
        activity: Activity,
        existingAuthorization: WalletAuthorizationSession?,
        prepareTransaction: suspend (WalletAuthorizationSession) -> PreparedWalletTransaction
    ): SignedWalletTransaction {
        return withAuthorizedWalletClient(
            activity,
            existingAuthorization?.let {
                WalletAuthorization(
                    ownerWalletAddress = it.ownerWalletAddress,
                    authToken = it.authToken
                )
            }
        ) { client, authorization ->
            val prepared = prepareTransaction(
                WalletAuthorizationSession(
                    ownerWalletAddress = authorization.ownerWalletAddress,
                    authToken = authorization.authToken
                )
            )
            val signResult = withContext(Dispatchers.IO) {
                client.signAndSendTransactions(
                    arrayOf(Base64.getDecoder().decode(prepared.transactionBase64)),
                    prepared.minContextSlot
                ).get()
            }
            val signature = signResult.signatures.firstOrNull()?.let { encodeBase58(it) }
                ?: error("Wallet did not return a signature.")
            SignedWalletTransaction(
                authorization = WalletAuthorizationSession(
                    ownerWalletAddress = authorization.ownerWalletAddress,
                    authToken = authorization.authToken
                ),
                signature = signature
            )
        }
    }

    suspend fun signInWithChallenge(
        activity: Activity,
        challengeMessage: String
    ): SignedWalletMessage {
        return withAuthorizedWalletClient(activity, null) { client, authorization ->
            val addressBytes = decodeBase58(authorization.ownerWalletAddress)
            val result = withContext(Dispatchers.IO) {
                client.signMessagesDetached(
                    arrayOf(challengeMessage.toByteArray(Charsets.UTF_8)),
                    arrayOf(addressBytes)
                ).get()
            }
            val signed = result.messages.firstOrNull() ?: error("Wallet did not return a signed message.")
            val signature = signed.signatures.firstOrNull()?.let { Base64.getEncoder().encodeToString(it) }
                ?: error("Wallet did not return a detached signature.")
            SignedWalletMessage(
                authorization = WalletAuthorizationSession(
                    ownerWalletAddress = authorization.ownerWalletAddress,
                    authToken = authorization.authToken
                ),
                signatureBase64 = signature,
                signedMessage = challengeMessage
            )
        }
    }

    private suspend fun <T> withAuthorizedWalletClient(
        activity: Activity,
        existingAuthorization: WalletAuthorization?,
        action: suspend (MobileWalletAdapterClient, WalletAuthorization) -> T
    ): T {
        if (!LocalAssociationIntentCreator.isWalletEndpointAvailable(activity.packageManager)) {
            error("No Solana wallet app found for Mobile Wallet Adapter.")
        }

        var scenario: LocalAssociationScenario? = null
        try {
            scenario = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
            val clientFuture = scenario.start()

            withContext(Dispatchers.Main) {
                val intent = LocalAssociationIntentCreator.createAssociationIntent(
                    null,
                    scenario.getPort(),
                    scenario.getSession()
                )
                activity.startActivity(intent)
            }

            val client = withContext(Dispatchers.IO) { clientFuture.get() }
            val authResult = withContext(Dispatchers.IO) {
                if (existingAuthorization?.authToken.isNullOrBlank()) {
                    client.authorize(
                        Uri.parse("https://soulmint.app"),
                        Uri.parse("icon.png"),
                        "SoulMint",
                        "mainnet-beta"
                    ).get()
                } else {
                    client.reauthorize(
                        Uri.parse("https://soulmint.app"),
                        Uri.parse("icon.png"),
                        "SoulMint",
                        existingAuthorization!!.authToken!!
                    ).get()
                }
            }

            val account = authResult.accounts?.firstOrNull()?.publicKey ?: authResult.publicKey
            val ownerWalletAddress = account?.let { encodeBase58(it) } ?: error("Wallet did not return a public key.")
            return action(
                client,
                WalletAuthorization(
                    ownerWalletAddress = ownerWalletAddress,
                    authToken = authResult.authToken ?: existingAuthorization?.authToken
                )
            )
        } finally {
            withContext(Dispatchers.IO) {
                scenario?.close()?.get()
            }
        }
    }

    private fun encodeBase58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        if (bytes.isEmpty()) return ""

        val input = bytes.copyOf()
        val encoded = CharArray(input.size * 2)
        var outputStart = encoded.size
        var inputStart = input.indexOfFirst { it.toInt() != 0 }
        if (inputStart == -1) {
            return "1".repeat(input.size)
        }

        while (inputStart < input.size) {
            var remainder = 0
            for (index in inputStart until input.size) {
                val digit = input[index].toInt() and 0xff
                val temp = remainder * 256 + digit
                input[index] = (temp / 58).toByte()
                remainder = temp % 58
            }
            encoded[--outputStart] = alphabet[remainder]
            while (inputStart < input.size && input[inputStart].toInt() == 0) {
                inputStart += 1
            }
        }

        bytes.takeWhile { it.toInt() == 0 }.forEach {
            encoded[--outputStart] = '1'
        }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    private fun decodeBase58(value: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base58 = IntArray(128) { -1 }
        alphabet.forEachIndexed { index, c -> base58[c.code] = index }
        if (value.isEmpty()) return ByteArray(0)

        val input58 = IntArray(value.length)
        value.forEachIndexed { index, char ->
            val digit = if (char.code < 128) base58[char.code] else -1
            require(digit >= 0) { "Invalid Base58 character: $char" }
            input58[index] = digit
        }

        var zeros = 0
        while (zeros < input58.size && input58[zeros] == 0) zeros += 1

        val decoded = ByteArray(value.length)
        var outputStart = decoded.size
        var inputStart = zeros

        while (inputStart < input58.size) {
            var remainder = 0
            for (index in inputStart until input58.size) {
                val temp = remainder * 58 + input58[index]
                input58[index] = temp / 256
                remainder = temp % 256
            }
            decoded[--outputStart] = remainder.toByte()
            while (inputStart < input58.size && input58[inputStart] == 0) {
                inputStart += 1
            }
        }

        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
            outputStart += 1
        }

        return ByteArray(zeros + decoded.size - outputStart).also { output ->
            repeat(zeros) { output[it] = 0 }
            decoded.copyInto(output, zeros, outputStart, decoded.size)
        }
    }
}

data class PreparedWalletTransaction(
    val transactionBase64: String,
    val minContextSlot: Int?
)

data class SignedWalletTransaction(
    val authorization: WalletAuthorizationSession,
    val signature: String
)

private data class WalletAuthorization(
    val ownerWalletAddress: String,
    val authToken: String?
)
