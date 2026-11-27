package com.example.productexplorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du ViewModel.
 * À placer dans : app/src/test/java/com/example/productexplorer/
 */
class ProductCatalogViewModelTest {

    @Test
    fun `au demarrage tous les produits sont affiches`() {
        val viewModel = ProductCatalogViewModel()

        assertEquals(sampleProducts().size, viewModel.uiState.value.products.size)
    }

    @Test
    fun `la recherche filtre sur le titre, la marque et la categorie`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onSearchQueryChange("audio")

        val ids = viewModel.uiState.value.products.map { it.id }
        assertEquals(listOf(2, 4), ids)
    }

    @Test
    fun `le filtre stock retire les produits en rupture`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onToggleStockFilter()

        assertTrue(viewModel.uiState.value.products.all { it.stock > 0 })
    }

    @Test
    fun `cliquer deux fois sur une categorie la deselectionne`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onCategoryClick("Wearables")
        assertEquals(listOf(3), viewModel.uiState.value.products.map { it.id })

        viewModel.onCategoryClick("Wearables")
        assertEquals(null, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun `le tri par prix croissant utilise le prix remise`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onSortOptionChange(SortOption.PRICE_ASC)

        val prices = viewModel.uiState.value.products.map { it.finalPrice }
        assertEquals(prices.sorted(), prices)
    }

    @Test
    fun `ajouter puis retirer un favori`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onFavoriteClick(1)
        assertEquals(listOf(1), viewModel.uiState.value.favoriteProductIds)

        viewModel.onFavoriteClick(1)
        assertTrue(viewModel.uiState.value.favoriteProductIds.isEmpty())
    }

    @Test
    fun `un produit en rupture ne peut pas etre ajoute au panier`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onAddToCart(2) // Casque Audio Pulse : stock = 0

        assertEquals(0, viewModel.uiState.value.cartItemCount)
    }

    @Test
    fun `le total du panier tient compte des quantites et des remises`() {
        val viewModel = ProductCatalogViewModel()

        viewModel.onAddToCart(4)
        viewModel.onAddToCart(4)

        val expected = 59.99 * 0.95 * 2
        assertEquals(2, viewModel.uiState.value.cartItemCount)
        assertEquals(expected, viewModel.uiState.value.cartTotal, 0.001)
    }

    @Test
    fun `la quantite ne depasse jamais le stock`() {
        val viewModel = ProductCatalogViewModel()
        val stock = viewModel.getProductById(5)!!.stock

        repeat(stock + 3) { viewModel.onAddToCart(5) }

        assertEquals(stock, viewModel.uiState.value.cartQuantities[5])
    }

    @Test
    fun `reinitialiser les filtres remet tout par defaut`() {
        val viewModel = ProductCatalogViewModel()
        viewModel.onSearchQueryChange("toto")
        viewModel.onToggleStockFilter()
        viewModel.onSortOptionChange(SortOption.RATING)

        viewModel.onResetFilters()

        val state = viewModel.uiState.value
        assertEquals(false, state.hasActiveFilters)
        assertEquals(sampleProducts().size, state.products.size)
    }
}
