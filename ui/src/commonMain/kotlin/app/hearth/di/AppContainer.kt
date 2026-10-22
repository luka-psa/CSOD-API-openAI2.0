package app.hearth.di

import app.hearth.ai.Orchestrator
import app.hearth.ai.llm.LlmException
import app.hearth.ai.llm.LlmProvider
import app.hearth.ai.llm.ModelRole
import app.hearth.ai.llm.ProviderConfig
import app.hearth.ai.llm.createProvider
import app.hearth.ai.route.Prompts
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inbox.InboxRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Where each platform keeps the AI settings (encrypted on Android, this browser only on the web). */
interface SettingsStore {
    fun loadAi(): ProviderConfig?
    fun saveAi(config: ProviderConfig?)
}

/**
 * Hand-wired dependencies (no DI framework, to stay light on low-spec tablets).
 * Each platform opens the database its own way and passes it in.
 */
class AppContainer(db: HearthDatabase, val lang: Lang, private val settings: SettingsStore) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val shopping = ShoppingRepository(db)
    val inbox = InboxRepository(db)
    val tools = HearthTools(shopping, inbox)

    private val _ai = MutableStateFlow(settings.loadAi())
    val ai: StateFlow<ProviderConfig?> = _ai.asStateFlow()
    private var cached: Pair<ProviderConfig, LlmProvider>? = null

    val orchestrator = Orchestrator(tools.registry, provider = ::provider)

    fun context(source: ToolSource) = ToolContext(source = source, lang = lang)

    fun saveAi(config: ProviderConfig?) {
        settings.saveAi(config)
        _ai.value = config
    }

    /** Sends one tiny routing request; returns null when it works, otherwise a message for the person. */
    suspend fun testAi(config: ProviderConfig): String? = try {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("ok") { put("type", "boolean") } }
            put("required", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("ok"))))
            put("additionalProperties", false)
        }
        createProvider(config).structured(ModelRole.ROUTER, Prompts.ROUTER, "Answer with ok = true.", "check", schema)
        null
    } catch (e: LlmException) {
        e.message ?: e.reason.name
    }

    private fun provider(): LlmProvider? {
        val config = _ai.value ?: return null
        cached?.let { (c, p) -> if (c == config) return p }
        return createProvider(config).also { cached = config to it }
    }

    init {
        appScope.launch { shopping.ensureDefaultShops(lang) }
    }
}
