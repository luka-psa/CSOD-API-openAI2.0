package app.hearth

import android.app.Application
import app.hearth.di.AppContainer

class HearthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
