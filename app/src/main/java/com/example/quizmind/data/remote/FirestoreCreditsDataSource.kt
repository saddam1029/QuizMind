package com.example.quizmind.data.remote

import android.util.Log
import com.example.quizmind.data.credits.CreditPolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CreditDeduction(
    val remainingCredits: Long,
    val deducted: Long
)

@Singleton
class FirestoreCreditsDataSource @Inject constructor() {

    companion object {
        private const val TAG = "FirestoreCredits"
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    fun observeCredits(): Flow<Long> {
        val user = auth.currentUser
        if (user == null) {
            return flowOf(0L)
        }

        return callbackFlow {
            // Show a safe default immediately so Home never waits on network.
            trySend(CreditPolicy.STARTING_CREDITS)

            try {
                ensureUserDoc(user.uid)
            } catch (e: Exception) {
                Log.w(TAG, "ensureUserDoc skipped (offline or unavailable): ${e.message}")
            }

            val registration = userDoc(user.uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Credits listener error: ${error.message}")
                    // Keep last known / default; do not crash.
                    if (snapshot == null) {
                        trySend(CreditPolicy.STARTING_CREDITS)
                    }
                    return@addSnapshotListener
                }
                val credits = snapshot?.getLong("credits") ?: CreditPolicy.STARTING_CREDITS
                trySend(credits)
            }

            awaitClose { registration.remove() }
        }.catch { error ->
            Log.w(TAG, "Credits flow failed: ${error.message}")
            emit(CreditPolicy.STARTING_CREDITS)
        }
    }

    suspend fun getCredits(): Long = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext 0L
        try {
            ensureUserDoc(user.uid)
            readCredits(user.uid)
        } catch (e: Exception) {
            Log.w(TAG, "getCredits fallback: ${e.message}")
            CreditPolicy.STARTING_CREDITS
        }
    }

    /**
     * Instant soft balance for generate-quiz gate.
     * Cache / memory only — never waits on a server round-trip.
     */
    suspend fun getCachedCredits(): Long = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext 0L
        try {
            val cache = userDoc(user.uid).get(Source.CACHE).await()
            cache.getLong("credits") ?: CreditPolicy.STARTING_CREDITS
        } catch (e: Exception) {
            Log.w(TAG, "getCachedCredits fallback: ${e.message}")
            CreditPolicy.STARTING_CREDITS
        }
    }

    suspend fun ensureEnoughCredits(required: Long): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Please sign in to generate quizzes."))
            ensureUserDoc(user.uid)
            val current = readCredits(user.uid)
            if (current < required) {
                Result.failure(
                    IllegalStateException(
                        "Insufficient credits. You need $required credits but have only $current."
                    )
                )
            } else {
                Result.success(current)
            }
        } catch (e: Exception) {
            if (isOfflineError(e)) {
                Result.failure(
                    IllegalStateException(
                        "You appear to be offline. Connect to the internet to generate a quiz."
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Deducts credits only after a successful generation.
     * Uses a Firestore transaction so two parallel requests cannot overspend.
     */
    suspend fun deductCredits(
        amount: Long,
        quizId: String,
        reason: String
    ): Result<CreditDeduction> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Please sign in to generate quizzes."))
            if (amount <= 0L) {
                val current = getCredits()
                return@withContext Result.success(CreditDeduction(current, 0L))
            }

            val result = firestore.runTransaction { transaction ->
                val ref = userDoc(user.uid)
                val snapshot = transaction.get(ref)
                val current = if (snapshot.exists()) {
                    snapshot.getLong("credits") ?: CreditPolicy.STARTING_CREDITS
                } else {
                    CreditPolicy.STARTING_CREDITS
                }
                if (current < amount) {
                    throw IllegalStateException(
                        "Insufficient credits. You need $amount credits but have only $current."
                    )
                }
                val remaining = current - amount
                transaction.set(
                    ref,
                    mapOf(
                        "credits" to remaining,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: "")
                    ),
                    SetOptions.merge()
                )
                val ledgerRef = ref.collection("credit_ledger").document()
                transaction.set(
                    ledgerRef,
                    mapOf(
                        "amount" to -amount,
                        "quizId" to quizId,
                        "reason" to reason,
                        "remaining" to remaining,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                CreditDeduction(remainingCredits = remaining, deducted = amount)
            }.await()

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readCredits(uid: String): Long {
        return try {
            val server = userDoc(uid).get(Source.SERVER).await()
            server.getLong("credits") ?: CreditPolicy.STARTING_CREDITS
        } catch (serverError: Exception) {
            try {
                val cache = userDoc(uid).get(Source.CACHE).await()
                cache.getLong("credits") ?: CreditPolicy.STARTING_CREDITS
            } catch (_: Exception) {
                Log.w(TAG, "Credits unavailable from server/cache: ${serverError.message}")
                CreditPolicy.STARTING_CREDITS
            }
        }
    }

    private suspend fun ensureUserDoc(uid: String) {
        val ref = userDoc(uid)
        val snapshot = try {
            ref.get(Source.CACHE).await()
        } catch (_: Exception) {
            try {
                ref.get(Source.SERVER).await()
            } catch (e: Exception) {
                // First launch / offline: create the doc so it syncs when online.
                Log.w(TAG, "Reading user doc failed, creating defaults: ${e.message}")
                null
            }
        }

        if (snapshot == null || !snapshot.exists() || snapshot.getLong("credits") == null) {
            val user = auth.currentUser
            try {
                ref.set(
                    mapOf(
                        "credits" to CreditPolicy.STARTING_CREDITS,
                        "email" to (user?.email ?: ""),
                        "displayName" to (user?.displayName ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                // Offline write is usually queued; if it still fails, Home keeps the default UI value.
                Log.w(TAG, "Creating user credits doc failed: ${e.message}")
            }
        }
    }

    private fun isOfflineError(error: Throwable): Boolean {
        if (error is FirebaseFirestoreException) {
            return error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                error.message?.contains("offline", ignoreCase = true) == true
        }
        return error.message?.contains("offline", ignoreCase = true) == true
    }
}
