package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A recipe from a link: the page's JSON-LD becomes one of our recipes, with its photo. */
class RecipeImportTest {
    private class FakePages(val pages: Map<String, String>) : PageFetcher {
        val images = mutableListOf<String>()
        override suspend fun page(url: String) = pages[url] ?: error("404")
        override suspend fun image(url: String): ByteArray { images += url; return byteArrayOf(1, 2, 3) }
    }

    private val page = """
        <html><head><title>Lasagne</title>
        <script type="application/ld+json">{"@context":"https://schema.org","@graph":[
          {"@type":"WebPage","name":"Site"},
          {"@type":["Recipe"],"name":"Lasagne van oma &amp; co","recipeYield":["4","4 personen"],
           "image":{"@type":"ImageObject","url":"https://kook.be/lasagne.jpg"},
           "recipeIngredient":["500 g gehakt","1 ui, fijngesnipperd","Lasagnebladen","500 g gehakt"],
           "recipeInstructions":[{"@type":"HowToSection","name":"Saus","itemListElement":[
              {"@type":"HowToStep","text":"Bak het <b>gehakt</b> rul."},{"@type":"HowToStep","text":"Voeg de ui toe."}]},
              {"@type":"HowToStep","text":"Bak 40 minuten op 180&#176;."}]}
        ]}</script></head><body>...</body></html>
    """.trimIndent()

    private val db = inMemoryHearthDatabase()
    private val week = WeekRepository(db)
    private val pages = FakePages(mapOf("https://kook.be/lasagne" to page, "https://kook.be/blog" to "<html><title>Blog</title></html>"))
    private val tools = HearthTools(ShoppingRepository(db), BacklogRepository(db), BoardRepository(db), StockRepository(db), week, SettingsRepository(db), MeetingRepository(db), pages = pages)
    private val ctx = ToolContext(ToolSource.CHAT, Lang.NL)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `a recipe page becomes our recipe with its steps, source and photo`() = runTest {
        val result = tools.registry.run(tools.importRecipe, ImportRecipeArgs("https://kook.be/lasagne"), ctx)
        assertEquals("Recept bewaard: Lasagne van oma & co (3 ingrediënten). Plan het in het weekmenu wanneer je wilt.", result.summary)
        val saved = week.recipe("Lasagne van oma & co")!!
        // Lines stay whole: "1 ui, fijngesnipperd" is one ingredient.
        assertEquals(listOf("500 g gehakt", "1 ui, fijngesnipperd", "Lasagnebladen"), saved.ingredients)
        assertEquals(
            "Voor 4\n1. Bak het gehakt rul.\n2. Voeg de ui toe.\n3. Bak 40 minuten op 180°.\nBron: https://kook.be/lasagne",
            saved.notes,
        )
        assertTrue(saved.hasPhoto)
        assertEquals(listOf("https://kook.be/lasagne.jpg"), pages.images)
    }

    @Test
    fun `a page without a recipe or a broken link says so kindly`() = runTest {
        assertNull(readRecipePage("<html><title>Blog</title></html>"))
        val none = assertFailsWith<ToolException> { tools.registry.run(tools.importRecipe, ImportRecipeArgs("https://kook.be/blog"), ctx) }
        assertTrue("geen recept" in none.userMessage)
        val broken = assertFailsWith<ToolException> { tools.registry.run(tools.importRecipe, ImportRecipeArgs("https://kook.be/weg"), ctx) }
        assertTrue("niet openen" in broken.userMessage)
        assertFailsWith<ToolException> { tools.registry.run(tools.importRecipe, ImportRecipeArgs("lasagne"), ctx) }
    }
}
