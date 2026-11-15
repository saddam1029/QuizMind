package com.example.quizmind.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.quizmind.R
import com.example.quizmind.utils.AppPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        // Short branded pause only — do not block startup for seconds.
        private const val MIN_SPLASH_MS = 700L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        // Keep the system splash until the first frame of our layout is ready.
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }
        window.decorView.post { keepSplash = false }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val next = resolveNextScreen()
            val elapsed = System.currentTimeMillis() - started
            val remaining = MIN_SPLASH_MS - elapsed
            if (remaining > 0) delay(remaining)
            startActivity(next)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun resolveNextScreen(): Intent {
        val auth = FirebaseAuth.getInstance()
        return when {
            !AppPreferences.isOnboardingCompleted(this) ->
                Intent(this, OnboardingActivity::class.java)
            auth.currentUser != null ->
                Intent(this, MainActivity::class.java)
            else ->
                Intent(this, LoginActivity::class.java)
        }
    }
}
