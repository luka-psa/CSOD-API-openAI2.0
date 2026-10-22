package app.hearth

import android.app.Application
import app.hearth.core.data.openHearthDatabase
import app.hearth.core.model.Lang
import app.hearth.di.AppContainer
import java.util.Locale

class HearthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(openHearthDatabase(this), Lang.fromTag(Locale.getDefault().toLanguageTag()))
    }
}
