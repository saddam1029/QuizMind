
package com.example.quizmind.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CustomCredential
import com.example.quizmind.MainActivity
import com.example.quizmind.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.example.quizmind.R
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle status bar and navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }
    }

    override fun onStart() {
        super.onStart()

        if (auth.currentUser != null) {
            navigateToMain()
        }
    }

    private fun startGoogleSignIn() {

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(
                getString(R.string.default_web_client_id)
            )
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )

                handleSignIn(result)

            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Google sign-in cancelled")

            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error", e)
                showMessage("Unable to sign in. Please try again.")

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected sign-in error", e)
                showMessage("Something went wrong. Please try again.")
            }
        }
    }

    private fun handleSignIn(
        result: androidx.credentials.GetCredentialResponse
    ) {
        val credential = result.credential

        if (
            credential is CustomCredential &&
            credential.type ==
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                firebaseAuthWithGoogle(googleCredential.idToken)

            } catch (e: Exception) {
                Log.e(TAG, "Unable to read Google ID token", e)
                showMessage("Unable to authenticate your Google account.")
            }

        } else {
            Log.w(TAG, "Unexpected credential type: ${credential.type}")
            showMessage("Unsupported sign-in credential.")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {

        val firebaseCredential =
            GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase Google sign-in successful")
                    navigateToMain()

                } else {
                    Log.e(
                        TAG,
                        "Firebase authentication failed",
                        task.exception
                    )

                    showMessage("Sign-in failed. Please try again.")
                }
            }
    }

    private fun navigateToMain() {
        if (isFinishing || isDestroyed) return

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}