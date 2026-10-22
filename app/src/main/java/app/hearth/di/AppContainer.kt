package app.hearth.di

import android.content.Context
import app.hearth.ai.Orchestrator
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Hand-wired dependencies (no DI framework, to stay light on low-spec tablets). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val lang: Lang = Lang.fromTag(Locale.getDefault().toLanguageTag())

    private val db = HearthDatabase.open(context)
    val shopping = ShoppingRepository(db)
    val tools = HearthTools(shopping)
    val orchestrator = Orchestrator(tools.registry)

    fun context(source: ToolSource) = ToolContext(source = source, lang = lang)

    init {
        appScope.launch { shopping.ensureDefaultShops(lang) }
    }
}
