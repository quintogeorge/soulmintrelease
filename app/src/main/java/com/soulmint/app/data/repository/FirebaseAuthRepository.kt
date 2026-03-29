package com.soulmint.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await

data class AuthSession(
    val uid: String,
    val isAnonymous: Boolean,
    val email: String? = null
)

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun currentSession(): AuthSession? {
        val user = auth.currentUser ?: return null
        return AuthSession(user.uid, user.isAnonymous, user.email)
    }

    suspend fun ensureAnonymousSession(): AuthSession {
        currentSession()?.let { return it }
        val result = auth.signInAnonymously().await()
        val user = result.user ?: error("Firebase anonymous sign-in returned no user.")
        return AuthSession(user.uid, user.isAnonymous, user.email)
    }

    suspend fun signInOrRegisterWithEmail(email: String, password: String): AuthSession {
        val normalizedEmail = email.trim()
        require(normalizedEmail.isNotEmpty()) { "Email is required." }
        require(password.length >= 6) { "Password must be at least 6 characters." }

        val currentUser = auth.currentUser
        val credential = EmailAuthProvider.getCredential(normalizedEmail, password)

        val user = when {
            currentUser?.isAnonymous == true -> {
                runCatching { currentUser.linkWithCredential(credential).await().user }
                    .getOrElse {
                        auth.signOut()
                        when ((it as? FirebaseAuthException)?.errorCode) {
                            "ERROR_EMAIL_ALREADY_IN_USE",
                            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> signInExistingEmailUser(normalizedEmail, password)
                            else -> signInOrCreateEmailUser(normalizedEmail, password)
                        }
                    }
            }
            else -> signInOrCreateEmailUser(normalizedEmail, password)
        } ?: error("Firebase email auth returned no user.")

        return AuthSession(user.uid, user.isAnonymous, user.email)
    }

    private suspend fun signInOrCreateEmailUser(
        email: String,
        password: String
    ) = runCatching {
        signInExistingEmailUser(email, password)
    }.getOrElse { signInError ->
        val errorCode = (signInError as? FirebaseAuthException)?.errorCode
        if (errorCode == "ERROR_USER_NOT_FOUND" || errorCode == "ERROR_INVALID_LOGIN_CREDENTIALS") {
            runCatching { auth.createUserWithEmailAndPassword(email, password).await().user }
                .getOrElse { createError ->
                    val createCode = (createError as? FirebaseAuthException)?.errorCode
                    if (createCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                        throw IllegalStateException("This email is already registered. Try signing in with the correct password.")
                    }
                    throw createError
                }
        } else {
            throw signInError
        }
    }

    private suspend fun signInExistingEmailUser(
        email: String,
        password: String
    ) = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user
    }.getOrElse { error ->
        val errorCode = (error as? FirebaseAuthException)?.errorCode
        val message = error.message.orEmpty().lowercase()
        if (
            errorCode == "ERROR_WRONG_PASSWORD" ||
            errorCode == "ERROR_INVALID_LOGIN_CREDENTIALS" ||
            message.contains("invalid login credentials") ||
            message.contains("supplied auth credential is incorrect")
        ) {
            throw IllegalStateException("Incorrect password for this email.")
        }
        throw error
    }

    suspend fun signInWithCustomToken(customToken: String): AuthSession {
        val user = auth.signInWithCustomToken(customToken).await().user
            ?: error("Firebase custom token sign-in returned no user.")
        return AuthSession(user.uid, user.isAnonymous, user.email)
    }

    fun signOut() {
        auth.signOut()
    }
}
