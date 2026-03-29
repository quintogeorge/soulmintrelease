package com.soulmint.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class FirebaseAuthTokenProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun requireIdToken(forceRefresh: Boolean = false): String {
        val currentUser = auth.currentUser
            ?: error("Firebase user is required before generating avatar variants.")

        val tokenResult = currentUser.getIdToken(forceRefresh).await()
        return tokenResult.token ?: error("Firebase ID token was empty.")
    }
}
