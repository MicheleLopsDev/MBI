package io.github.luposolitario.mbi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        // 1) elimina sempre il DB in build di debug
        if (BuildConfig.DEBUG) {
            deleteDatabase("app_database.db")
        }

        // 2) poi chiama il super: a questo punto Hilt procederà a creare i componenti
        super.onCreate()
    }

}
