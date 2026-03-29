package com.soulmint.data.repository

import com.soulmint.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private data class SignatureStatusesParams(
    @Json(name = "searchTransactionHistory")
    val searchTransactionHistory: Boolean = true
)

private data class SignatureStatusesRequest(
    @Json(name = "jsonrpc")
    val jsonRpc: String = "2.0",
    @Json(name = "id")
    val id: Int = 1,
    @Json(name = "method")
    val method: String = "getSignatureStatuses",
    @Json(name = "params")
    val params: List<Any> = emptyList()
)

private data class SignatureStatusValue(
    @Json(name = "confirmationStatus")
    val confirmationStatus: String? = null,
    @Json(name = "err")
    val error: Any? = null
)

private data class SignatureStatusesResult(
    @Json(name = "value")
    val value: List<SignatureStatusValue?>
)

private data class SignatureStatusesResponse(
    @Json(name = "result")
    val result: SignatureStatusesResult? = null
)

private data class GetBalanceValue(
    @Json(name = "value")
    val value: Long
)

private data class GetBalanceResponse(
    @Json(name = "result")
    val result: GetBalanceValue? = null
)

class SolanaRpcGateway(
    private val rpcUrl: String = BuildConfig.SOULMINT_SOLANA_RPC_URL
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val requestAdapter = moshi.adapter(SignatureStatusesRequest::class.java)
    private val responseAdapter = moshi.adapter(SignatureStatusesResponse::class.java)
    private val getBalanceResponseAdapter = moshi.adapter(GetBalanceResponse::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun awaitConfirmedSignature(
        signature: String,
        timeoutMs: Long = 90_000,
        pollIntervalMs: Long = 2_000
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val status = withContext(Dispatchers.IO) {
                fetchSignatureStatus(signature)
            }
            if (status?.error != null) {
                error("Mainnet rejected the mint transaction.")
            }
            if (status?.confirmationStatus == "confirmed" || status?.confirmationStatus == "finalized") {
                return
            }
            delay(pollIntervalMs)
        }
        error("Wallet returned a signature, but mainnet confirmation timed out.")
    }

    suspend fun requireSufficientMainnetBalance(
        walletAddress: String,
        minimumLamports: Long = 30_000_000L
    ) {
        val balance = withContext(Dispatchers.IO) {
            fetchBalance(walletAddress)
        }
        if (balance < minimumLamports) {
            val neededSol = minimumLamports / 1_000_000_000.0
            val currentSol = balance / 1_000_000_000.0
            error(
                "Wallet balance is too low for Solana mainnet minting. " +
                    "Need about %.3f SOL, current balance is %.6f SOL."
                        .format(neededSol, currentSol)
            )
        }
    }

    private fun fetchSignatureStatus(signature: String): SignatureStatusValue? {
        val body = requestAdapter.toJson(
            SignatureStatusesRequest(
                params = listOf(
                    listOf(signature),
                    SignatureStatusesParams()
                )
            )
        )
        val request = Request.Builder()
            .url(rpcUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unable to query Solana mainnet for transaction confirmation.")
            }
            val payload = response.body?.string().orEmpty()
            val parsed = responseAdapter.fromJson(payload)
            return parsed?.result?.value?.firstOrNull()
        }
    }

    private fun fetchBalance(walletAddress: String): Long {
        val body = requestAdapter.toJson(
            SignatureStatusesRequest(
                method = "getBalance",
                params = listOf(walletAddress)
            )
        )
        val request = Request.Builder()
            .url(rpcUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unable to query Solana mainnet wallet balance.")
            }
            val payload = response.body?.string().orEmpty()
            val parsed = getBalanceResponseAdapter.fromJson(payload)
            return parsed?.result?.value ?: error("Unable to read Solana mainnet wallet balance.")
        }
    }
}
