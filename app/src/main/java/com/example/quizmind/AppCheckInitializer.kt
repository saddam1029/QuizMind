package com.example.quizmind

import com.google.firebase.Firebase
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.appCheck

internal object AppCheckInitializer {

    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}