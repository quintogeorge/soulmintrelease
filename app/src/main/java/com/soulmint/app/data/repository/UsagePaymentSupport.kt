package com.soulmint.data.repository

import com.soulmint.data.model.UsagePaywall
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException

class UsagePaymentRequiredException(
    val paywall: UsagePaywall,
    message: String
) : IllegalStateException(message)

private val usagePaymentMoshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val usagePaywallErrorAdapter = usagePaymentMoshi.adapter(UsagePaywallErrorDto::class.java)

fun HttpException.toUsagePaymentExceptionOrNull(): UsagePaymentRequiredException? {
    if (code() != 402) return null
    val body = response()?.errorBody()?.string().orEmpty()
    val parsed = runCatching { usagePaywallErrorAdapter.fromJson(body) }.getOrNull() ?: return null
    val paywall = parsed.paywall ?: return null
    return UsagePaymentRequiredException(
        paywall = UsagePaywall(
            trigger = paywall.trigger,
            reference = paywall.reference,
            recipientWalletAddress = paywall.recipientWalletAddress,
            solAmount = paywall.solAmount,
            skrAmount = paywall.skrAmount,
            skrTokenMint = paywall.skrTokenMint,
            solanaPaySolUrl = paywall.solanaPaySolUrl,
            solanaPaySkrUrl = paywall.solanaPaySkrUrl
        ),
        message = parsed.error
    )
}
