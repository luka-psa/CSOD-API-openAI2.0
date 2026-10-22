package app.hearth.web

import app.hearth.ai.llm.ProviderConfig
import app.hearth.di.SettingsStore
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/** AI settings for the web preview: kept in this browser's local storage only. */
class BrowserSettingsStore : SettingsStore {
    override fun loadAi(): ProviderConfig? = runCatching {
        localStorage.getItem(KEY)?.let { Json.decodeFromString(ProviderConfig.serializer(), it) }
    }.getOrNull()

    override fun saveAi(config: ProviderConfig?) {
        runCatching {
            if (config == null) localStorage.removeItem(KEY)
            else localStorage.setItem(KEY, Json.encodeToString(ProviderConfig.serializer(), config))
        }
    }

    private companion object {
        const val KEY = "hearth.ai"
    }
}
