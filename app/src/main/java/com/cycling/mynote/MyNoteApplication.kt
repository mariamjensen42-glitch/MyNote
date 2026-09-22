package com.cycling.mynote

import android.app.Application
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyNoteApplication : Application() {

    @Inject
    lateinit var repoSession: RepoSession

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Loads the persisted folder grant before the first screen asks for it, so the app can
        // route straight to the library instead of flashing onboarding and then navigating away.
        applicationScope.launch { repoSession.ensureLoaded() }
    }
}
