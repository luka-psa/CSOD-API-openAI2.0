package app.hearth.core.tools.food

import app.hearth.core.model.Shop
import app.hearth.core.model.ShopKind
import app.hearth.core.model.normalizeName

/** Maps what people say ("de apotheek", "pharmacy", "Colruyt") to a shop. */
object ShopResolver {
    private val kindWords: Map<String, ShopKind> = buildMap {
        listOf("supermarkt", "supermarket", "winkel", "grocery store", "groceries", "shop", "store")
            .forEach { put(it, ShopKind.SUPERMARKET) }
        listOf("apotheek", "apotheker", "pharmacy", "pharmacist", "chemist", "drugstore")
            .forEach { put(it, ShopKind.PHARMACY) }
        listOf("bakker", "bakkerij", "bakery", "baker").forEach { put(it, ShopKind.BAKERY) }
        listOf("doe-het-zelf", "doe-het-zelfzaak", "bouwmarkt", "hardware store", "diy", "diy store")
            .forEach { put(it, ShopKind.DIY) }
        listOf("meubelzaak", "meubelwinkel", "furniture store").forEach { put(it, ShopKind.FURNITURE) }
    }

    /** Brand names that should become their own shop, but with a sensible kind. */
    private val brandKinds: Map<String, ShopKind> = buildMap {
        listOf("colruyt", "delhaize", "aldi", "lidl", "carrefour", "albert heijn", "ah", "okay", "spar", "jumbo")
            .forEach { put(it, ShopKind.SUPERMARKET) }
        listOf("gamma", "brico", "hubo", "praxis", "action").forEach { put(it, ShopKind.DIY) }
        listOf("ikea", "jysk").forEach { put(it, ShopKind.FURNITURE) }
        listOf("kruidvat", "di").forEach { put(it, ShopKind.PHARMACY) }
    }

    private val articles = Regex("^(de|het|den|the|a|een)\\s+")

    fun clean(spoken: String): String = normalizeName(spoken).replace(articles, "")

    /** Finds an existing shop, or null if this is a new shop name. */
    fun resolve(spoken: String, shops: List<Shop>): Shop? {
        val key = clean(spoken)
        shops.firstOrNull { normalizeName(it.name) == key }?.let { return it }
        val kind = kindWords[key] ?: return null
        return shops.firstOrNull { it.kind == kind }
    }

    fun guessKind(spoken: String): ShopKind {
        val key = clean(spoken)
        return kindWords[key] ?: brandKinds[key] ?: ShopKind.OTHER
    }

    fun defaultShop(shops: List<Shop>): Shop? =
        shops.firstOrNull { it.kind == ShopKind.SUPERMARKET } ?: shops.firstOrNull()
}
