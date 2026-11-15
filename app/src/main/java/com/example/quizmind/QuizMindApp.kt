package com.example.quizmind

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.Executors

@HiltAndroidApp
class QuizMindApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Keep startup light: init Firebase quickly, defer App Check off the first frame.
        Firebase.initialize(context = this)
        Executors.newSingleThreadExecutor().execute {
            runCatching { AppCheckInitializer.install() }
        }
    }
}
