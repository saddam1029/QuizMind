package com.example.quizmind

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QuizMindApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        Firebase.initialize(context = this)

        // Initialize App Check for the current build type
        AppCheckInitializer.install()
    }
}