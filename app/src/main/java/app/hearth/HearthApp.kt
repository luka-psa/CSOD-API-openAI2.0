package app.hearth

import android.app.Application
import app.hearth.core.data.openHearthDatabase
import app.hearth.core.model.Lang
import app.hearth.di.AppContainer
import app.hearth.google.AndroidGoogleAuth
import java.util.Locale

class HearthApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Google sign-in for the opt-in Drive backup and Calendar; the activity shows its consent screen. */
    val googleAuth by lazy { AndroidGoogleAuth(this) }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(openHearthDatabase(this), Lang.fromTag(Locale.getDefault().toLanguageTag()), googleAuth)
    }
}
