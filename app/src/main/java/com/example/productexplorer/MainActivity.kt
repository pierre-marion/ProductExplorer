package com.example.productexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.productexplorer.ui.theme.ProductExplorerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProductExplorerTheme {
                ProductExplorerApp()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Modèle
// ---------------------------------------------------------------------------

data class ProductUi(
    val id: Int,
    val title: String,
    val brand: String,
    val category: String,
    val description: String,
    val price: Double,
    val discountPercentage: Double,
    val rating: Double,
    val stock: Int,
    val warrantyInformation: String,
    val shippingInformation: String
)

/** Prix après application de la remise. */
val ProductUi.finalPrice: Double
    get() = price * (1 - discountPercentage / 100)

val ProductUi.isInStock: Boolean
    get() = stock > 0

/** Une ligne du panier : un produit et la quantité choisie. */
data class CartLineUi(
    val product: ProductUi,
    val quantity: Int
) {
    val totalPrice: Double
        get() = product.finalPrice * quantity
}

/** Options de tri proposées dans le catalogue. */
enum class SortOption(val label: String) {
    DEFAULT("Par défaut"),
    PRICE_ASC("Prix croissant"),
    PRICE_DESC("Prix décroissant"),
    RATING("Meilleure note")
}

data class ProductCatalogUiState(
    val products: List<ProductUi> = emptyList(),
    val categories: List<String> = emptyList(),
    val searchQuery: String = "",
    val showOnlyInStock: Boolean = false,
    val showOnlyFavorites: Boolean = false,
    val selectedCategory: String? = null,
    val sortOption: SortOption = SortOption.DEFAULT,
    val favoriteProductIds: List<Int> = emptyList(),
    val cartQuantities: Map<Int, Int> = emptyMap(),
    val cartLines: List<CartLineUi> = emptyList(),
    val featuredProduct: ProductUi? = null
) {
    val cartItemCount: Int
        get() = cartLines.sumOf { it.quantity }

    val cartTotal: Double
        get() = cartLines.sumOf { it.totalPrice }

    val hasActiveFilters: Boolean
        get() = searchQuery.isNotEmpty() ||
                showOnlyInStock ||
                showOnlyFavorites ||
                selectedCategory != null ||
                sortOption != SortOption.DEFAULT
}

const val LOW_STOCK_THRESHOLD = 10

// ---------------------------------------------------------------------------
// Formatage
// ---------------------------------------------------------------------------

/** 699.99 -> "699,99 €" (format français). */
fun Double.toPriceText(): String = String.format(Locale.FRANCE, "%.2f €", this)

/** 12.5 -> "-12,5 %", 8.0 -> "-8 %". */
fun Double.toDiscountText(): String {
    val value = if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        String.format(Locale.FRANCE, "%.1f", this)
    }
    return "-$value %"
}

// ---------------------------------------------------------------------------
// Données d'exemple
// ---------------------------------------------------------------------------

fun sampleProducts(): List<ProductUi> {
    return listOf(
        sampleProduct(),
        ProductUi(
            id = 2,
            title = "Casque Audio Pulse",
            brand = "SoundPeak",
            category = "Audio",
            description = "Un casque confortable pour écouter de la musique et travailler dans de bonnes conditions.",
            price = 129.99,
            discountPercentage = 8.0,
            rating = 4.2,
            stock = 0,
            warrantyInformation = "Garantie constructeur : 1 an",
            shippingInformation = "Produit temporairement indisponible"
        ),
        ProductUi(
            id = 3,
            title = "Montre Connectée FitTime",
            brand = "FitTime",
            category = "Wearables",
            description = "Une montre connectée simple pour suivre l’activité quotidienne.",
            price = 89.99,
            discountPercentage = 15.0,
            rating = 4.4,
            stock = 18,
            warrantyInformation = "Garantie constructeur : 2 ans",
            shippingInformation = "Livraison estimée : 2 à 4 jours ouvrés"
        ),
        ProductUi(
            id = 4,
            title = "Enceinte Mini Boom",
            brand = "BoomSound",
            category = "Audio",
            description = "Une enceinte compacte pour écouter de la musique à la maison ou en déplacement.",
            price = 59.99,
            discountPercentage = 5.0,
            rating = 4.1,
            stock = 52,
            warrantyInformation = "Garantie constructeur : 1 an",
            shippingInformation = "Livraison estimée : 3 jours ouvrés"
        ),
        ProductUi(
            id = 5,
            title = "Lampe Connectée Lumo",
            brand = "HomeLight",
            category = "Maison",
            description = "Une lampe connectée dont on règle la couleur et l’intensité depuis son téléphone.",
            price = 39.99,
            discountPercentage = 0.0,
            rating = 3.9,
            stock = 4,
            warrantyInformation = "Garantie constructeur : 1 an",
            shippingInformation = "Livraison estimée : 2 jours ouvrés"
        )
    )
}

fun sampleProduct(): ProductUi {
    return ProductUi(
        id = 1,
        title = "Smartphone Toto X",
        brand = "TotoTech",
        category = "Smartphones",
        description = "Un smartphone léger avec un écran lumineux, une bonne autonomie et un design moderne.",
        price = 699.99,
        discountPercentage = 12.5,
        rating = 4.6,
        stock = 34,
        warrantyInformation = "Garantie constructeur : 2 ans",
        shippingInformation = "Livraison estimée : 3 à 5 jours ouvrés"
    )
}

fun sampleProductOutOfStock(): ProductUi {
    return ProductUi(
        id = 2,
        title = "Casque Audio Pulse",
        brand = "SoundPeak",
        category = "Audio",
        description = "Un casque confortable conçu pour écouter de la musique, " +
                "suivre des cours en ligne et travailler dans de bonnes conditions.",
        price = 129.99,
        discountPercentage = 8.0,
        rating = 4.2,
        stock = 0,
        warrantyInformation = "Garantie constructeur : 1 an",
        shippingInformation = "Produit temporairement indisponible"
    )
}

fun sampleCategories(): List<String> {
    return listOf("Smartphones", "Audio", "Wearables", "Maison")
}

fun sampleCartLines(): List<CartLineUi> {
    val products = sampleProducts()
    return listOf(
        CartLineUi(product = products[0], quantity = 1),
        CartLineUi(product = products[3], quantity = 2)
    )
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ProductCatalogViewModel : ViewModel() {

    private val allProducts: List<ProductUi> = sampleProducts()
    private val allCategories: List<String> = sampleCategories()

    private val _uiState = MutableStateFlow(
        ProductCatalogUiState(categories = allCategories).recompute()
    )

    val uiState: StateFlow<ProductCatalogUiState> = _uiState.asStateFlow()

    /**
     * Recalcule toutes les valeurs dérivées (liste filtrée et triée, panier,
     * produit mis en avant) à partir des critères de l'état.
     * Toutes les mises à jour passent par ici : l'état reste toujours cohérent.
     */
    private fun ProductCatalogUiState.recompute(): ProductCatalogUiState {
        val query = searchQuery.trim()

        val filteredProducts = allProducts.filter { product ->
            val matchesSearch = query.isEmpty() ||
                    product.title.contains(query, ignoreCase = true) ||
                    product.brand.contains(query, ignoreCase = true) ||
                    product.category.contains(query, ignoreCase = true)
            val matchesStock = !showOnlyInStock || product.isInStock
            val matchesFavorites = !showOnlyFavorites || product.id in favoriteProductIds
            val matchesCategory = selectedCategory == null || product.category == selectedCategory

            matchesSearch && matchesStock && matchesFavorites && matchesCategory
        }

        val sortedProducts = when (sortOption) {
            SortOption.DEFAULT -> filteredProducts
            SortOption.PRICE_ASC -> filteredProducts.sortedBy { it.finalPrice }
            SortOption.PRICE_DESC -> filteredProducts.sortedByDescending { it.finalPrice }
            SortOption.RATING -> filteredProducts.sortedByDescending { it.rating }
        }

        val newCartLines = cartQuantities.mapNotNull { (productId, quantity) ->
            getProductById(productId)?.let { product -> CartLineUi(product, quantity) }
        }

        return copy(
            products = sortedProducts,
            cartLines = newCartLines,
            featuredProduct = allProducts
                .filter { it.isInStock }
                .maxByOrNull { it.rating }
        )
    }

    private fun updateState(transform: (ProductCatalogUiState) -> ProductCatalogUiState) {
        _uiState.update { currentState -> transform(currentState).recompute() }
    }

    fun getProductById(productId: Int): ProductUi? {
        return allProducts.find { it.id == productId }
    }

    // --- Filtres et tri ---

    fun onSearchQueryChange(newValue: String) {
        updateState { it.copy(searchQuery = newValue) }
    }

    fun onToggleStockFilter() {
        updateState { it.copy(showOnlyInStock = !it.showOnlyInStock) }
    }

    fun onToggleFavoritesFilter() {
        updateState { it.copy(showOnlyFavorites = !it.showOnlyFavorites) }
    }

    /** Sélectionne une catégorie (ou aucune avec null). */
    fun onCategorySelected(category: String?) {
        updateState { it.copy(selectedCategory = category) }
    }

    /** Clic sur une puce : sélectionne la catégorie, ou la désélectionne si elle l'était déjà. */
    fun onCategoryClick(category: String) {
        updateState { currentState ->
            currentState.copy(
                selectedCategory = if (currentState.selectedCategory == category) null else category
            )
        }
    }

    fun onSortOptionChange(sortOption: SortOption) {
        updateState { it.copy(sortOption = sortOption) }
    }

    fun onResetFilters() {
        updateState { currentState ->
            currentState.copy(
                searchQuery = "",
                showOnlyInStock = false,
                showOnlyFavorites = false,
                selectedCategory = null,
                sortOption = SortOption.DEFAULT
            )
        }
    }

    // --- Favoris ---

    fun onFavoriteClick(productId: Int) {
        updateState { currentState ->
            val newFavoriteIds =
                if (currentState.favoriteProductIds.contains(productId)) {
                    currentState.favoriteProductIds - productId
                } else {
                    currentState.favoriteProductIds + productId
                }

            currentState.copy(favoriteProductIds = newFavoriteIds)
        }
    }

    // --- Panier ---

    /** Ajoute une unité au panier, sans jamais dépasser le stock disponible. */
    fun onAddToCart(productId: Int) {
        val product = getProductById(productId) ?: return
        updateState { currentState ->
            val currentQuantity = currentState.cartQuantities[productId] ?: 0
            if (currentQuantity >= product.stock) {
                currentState
            } else {
                currentState.copy(
                    cartQuantities = currentState.cartQuantities + (productId to currentQuantity + 1)
                )
            }
        }
    }

    /** Retire une unité ; supprime la ligne si la quantité tombe à 0. */
    fun onRemoveOneFromCart(productId: Int) {
        updateState { currentState ->
            val currentQuantity = currentState.cartQuantities[productId] ?: return@updateState currentState
            val newQuantities = if (currentQuantity <= 1) {
                currentState.cartQuantities - productId
            } else {
                currentState.cartQuantities + (productId to currentQuantity - 1)
            }
            currentState.copy(cartQuantities = newQuantities)
        }
    }

    fun onRemoveFromCart(productId: Int) {
        updateState { it.copy(cartQuantities = it.cartQuantities - productId) }
    }

    fun onClearCart() {
        updateState { it.copy(cartQuantities = emptyMap()) }
    }
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

enum class AppScreen(val title: String) {
    HOME("Product Explorer"),
    CATALOG("Catalogue"),
    DETAIL("Détail du produit"),
    CART("Mon panier")
}

/** Permet de sauvegarder la pile de navigation (rotation de l'écran, etc.). */
private val backStackSaver = listSaver<List<AppScreen>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map { AppScreen.valueOf(it) } }
)

@Composable
fun ProductExplorerApp(
    viewModel: ProductCatalogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var backStack by rememberSaveable(stateSaver = backStackSaver) {
        mutableStateOf(listOf(AppScreen.HOME))
    }
    var selectedProductId by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentScreen = backStack.last()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun navigateTo(screen: AppScreen) {
        if (backStack.last() != screen) {
            backStack = backStack + screen
        }
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    fun openProduct(product: ProductUi) {
        selectedProductId = product.id
        navigateTo(AppScreen.DETAIL)
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Le bouton "retour" du téléphone remonte dans la pile au lieu de fermer l'appli
    BackHandler(enabled = backStack.size > 1) {
        goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = currentScreen.title,
                canGoBack = backStack.size > 1,
                onBackClick = { goBack() },
                cartItemCount = uiState.cartItemCount,
                onCartClick = { navigateTo(AppScreen.CART) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)

        when (currentScreen) {
            AppScreen.HOME -> ProductHomeScreen(
                featuredProduct = uiState.featuredProduct,
                categories = uiState.categories,
                onFeaturedProductClick = { product -> openProduct(product) },
                onSearchClick = { navigateTo(AppScreen.CATALOG) },
                onCategoryClick = { category ->
                    viewModel.onCategorySelected(category)
                    navigateTo(AppScreen.CATALOG)
                },
                onSeeCatalogClick = {
                    viewModel.onCategorySelected(null)
                    navigateTo(AppScreen.CATALOG)
                },
                modifier = screenModifier
            )

            AppScreen.CATALOG -> ProductCatalogContainer(
                onProductClick = { product -> openProduct(product) },
                modifier = screenModifier,
                viewModel = viewModel
            )

            AppScreen.DETAIL -> {
                val product = selectedProductId?.let { viewModel.getProductById(it) }
                if (product == null) {
                    EmptyStateMessage(
                        title = "Produit introuvable",
                        message = "Ce produit n’existe plus.",
                        actionLabel = "Retour",
                        onActionClick = { goBack() },
                        modifier = screenModifier
                    )
                } else {
                    ProductDetailScreen(
                        product = product,
                        isFavorite = uiState.favoriteProductIds.contains(product.id),
                        onFavoriteClick = { viewModel.onFavoriteClick(product.id) },
                        quantityInCart = uiState.cartQuantities[product.id] ?: 0,
                        onAddToCartClick = {
                            viewModel.onAddToCart(product.id)
                            showMessage("${product.title} ajouté au panier")
                        },
                        modifier = screenModifier
                    )
                }
            }

            AppScreen.CART -> CartScreen(
                cartLines = uiState.cartLines,
                cartTotal = uiState.cartTotal,
                onIncreaseClick = viewModel::onAddToCart,
                onDecreaseClick = viewModel::onRemoveOneFromCart,
                onRemoveClick = viewModel::onRemoveFromCart,
                onProductClick = { product -> openProduct(product) },
                onBrowseCatalogClick = { navigateTo(AppScreen.CATALOG) },
                onCheckoutClick = {
                    viewModel.onClearCart()
                    showMessage("Commande validée, merci !")
                    backStack = listOf(AppScreen.HOME)
                },
                modifier = screenModifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    cartItemCount: Int,
    onCartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onCartClick) {
                BadgedBox(
                    badge = {
                        if (cartItemCount > 0) {
                            Badge { Text(text = "$cartItemCount") }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Panier, $cartItemCount article(s)"
                    )
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Composants réutilisables
// ---------------------------------------------------------------------------

@Composable
fun CategoryChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.extraLarge
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun CategoryRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            CategoryChip(
                label = category,
                isSelected = category == selectedCategory,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
fun SortOptionRow(
    selectedOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SortOption.entries) { option ->
            CategoryChip(
                label = option.label,
                isSelected = option == selectedOption,
                onClick = { onSortOptionChange(option) }
            )
        }
    }
}

@Composable
fun FavoriteIconButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (isFavorite) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Affiche l'état du stock avec une couleur adaptée. */
@Composable
fun StockLabel(
    stock: Int,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        stock <= 0 -> "Rupture de stock" to MaterialTheme.colorScheme.error
        stock < LOW_STOCK_THRESHOLD -> "Plus que $stock en stock !" to MaterialTheme.colorScheme.tertiary
        else -> "En stock ($stock unités)" to MaterialTheme.colorScheme.primary
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
    )
}

/** Prix barré + prix remisé si le produit est en promotion. */
@Composable
fun PriceText(
    price: Double,
    discountPercentage: Double,
    modifier: Modifier = Modifier
) {
    val finalPrice = price * (1 - discountPercentage / 100)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = finalPrice.toPriceText(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (discountPercentage > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = price.toPriceText(),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onActionClick) {
                Text(text = actionLabel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Écran catalogue
// ---------------------------------------------------------------------------

@Composable
fun ProductCatalogContainer(
    onProductClick: (ProductUi) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductCatalogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ProductCatalogScreen(
        products = uiState.products,
        categories = uiState.categories,
        searchQuery = uiState.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        showOnlyInStock = uiState.showOnlyInStock,
        onToggleStockFilter = viewModel::onToggleStockFilter,
        showOnlyFavorites = uiState.showOnlyFavorites,
        onToggleFavoritesFilter = viewModel::onToggleFavoritesFilter,
        selectedCategory = uiState.selectedCategory,
        onCategoryClick = viewModel::onCategoryClick,
        sortOption = uiState.sortOption,
        onSortOptionChange = viewModel::onSortOptionChange,
        hasActiveFilters = uiState.hasActiveFilters,
        onResetFilters = viewModel::onResetFilters,
        favoriteProductIds = uiState.favoriteProductIds,
        onFavoriteClick = viewModel::onFavoriteClick,
        onProductClick = onProductClick,
        modifier = modifier
    )
}

@Composable
fun ProductCatalogScreen(
    products: List<ProductUi>,
    categories: List<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showOnlyInStock: Boolean,
    onToggleStockFilter: () -> Unit,
    showOnlyFavorites: Boolean,
    onToggleFavoritesFilter: () -> Unit,
    selectedCategory: String?,
    onCategoryClick: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit,
    favoriteProductIds: List<Int>,
    onFavoriteClick: (Int) -> Unit,
    onProductClick: (ProductUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Catalogue produits",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = {
                    Text(text = "Rechercher un produit")
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer la recherche"
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleStockFilter) {
                    Text(
                        text = if (showOnlyInStock) {
                            "Afficher tous les produits"
                        } else {
                            "Afficher uniquement les produits en stock"
                        }
                    )
                }
                OutlinedButton(onClick = onToggleFavoritesFilter) {
                    Icon(
                        imageVector = if (showOnlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showOnlyFavorites) {
                            "Afficher tous les produits"
                        } else {
                            "Afficher uniquement mes favoris (${favoriteProductIds.size})"
                        }
                    )
                }
            }
        }

        item {
            Column {
                Text(text = "Catégories", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                CategoryRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategoryClick = onCategoryClick
                )
            }
        }

        item {
            Column {
                Text(text = "Trier par", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                SortOptionRow(
                    selectedOption = sortOption,
                    onSortOptionChange = onSortOptionChange
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${products.size} produit(s) affiché(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (hasActiveFilters) {
                    TextButton(onClick = onResetFilters) {
                        Text(text = "Réinitialiser")
                    }
                }
            }
        }

        if (products.isEmpty()) {
            item {
                EmptyStateMessage(
                    title = "Aucun produit trouvé",
                    message = "Essayez une autre recherche ou retirez des filtres.",
                    actionLabel = if (hasActiveFilters) "Réinitialiser les filtres" else null,
                    onActionClick = onResetFilters
                )
            }
        }

        items(
            items = products,
            key = { product -> product.id }
        ) { product ->
            ProductListItem(
                product = product,
                isFavorite = favoriteProductIds.contains(product.id),
                onFavoriteClick = {
                    onFavoriteClick(product.id)
                },
                onClick = {
                    onProductClick(product)
                }
            )
        }
    }
}

@Composable
fun ProductListItem(
    product: ProductUi,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${product.brand} • ${product.category}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                FavoriteIconButton(
                    isFavorite = isFavorite,
                    onClick = onFavoriteClick
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceText(
                    price = product.price,
                    discountPercentage = product.discountPercentage,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "★ ${product.rating}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (product.discountPercentage > 0) {
                Text(
                    text = "Promo ${product.discountPercentage.toDiscountText()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            StockLabel(stock = product.stock)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClick) {
                Text(text = "Voir le produit")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Écran d'accueil
// ---------------------------------------------------------------------------

@Composable
fun ProductHomeScreen(
    featuredProduct: ProductUi?,
    categories: List<String>,
    onFeaturedProductClick: (ProductUi) -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onSeeCatalogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HomeHeader()
        Spacer(modifier = Modifier.height(24.dp))
        SearchPreviewBar(onClick = onSearchClick)
        Spacer(modifier = Modifier.height(24.dp))
        if (featuredProduct != null) {
            FeaturedProductSection(
                product = featuredProduct,
                onClick = { onFeaturedProductClick(featuredProduct) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        CategoriesSection(
            categories = categories,
            onCategoryClick = onCategoryClick
        )
        Spacer(modifier = Modifier.height(24.dp))
        DailyOfferBox()
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSeeCatalogClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Voir tout le catalogue")
        }
    }
}

@Composable
fun HomeHeader(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Product Explorer",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Découvrez les produits du moment",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SearchPreviewBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rechercher un produit...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FeaturedProductSection(
    product: ProductUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Produit mis en avant",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = product.title,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = product.brand,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProductQuickInfoRow(
                price = product.finalPrice,
                rating = product.rating,
                stock = product.stock
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text(text = "Voir le produit")
            }
        }
    }
}

@Composable
fun ProductQuickInfoRow(
    price: Double,
    rating: Double,
    stock: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = price.toPriceText(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "★ $rating",
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (stock > 0) "$stock en stock" else "Rupture",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CategoriesSection(
    categories: List<String>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Catégories",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        CategoryRow(
            categories = categories,
            selectedCategory = null,
            onCategoryClick = onCategoryClick
        )
    }
}

@Composable
fun DailyOfferBox(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Offre du jour",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Jusqu’à -20 % sur une sélection de produits",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 6.dp
        ) {
            Text(
                text = "Nouveau",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Écran détail
// ---------------------------------------------------------------------------

@Composable
fun ProductDetailScreen(
    product: ProductUi,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    quantityInCart: Int,
    onAddToCartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canAddToCart = quantityInCart < product.stock

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ProductImage(
            productTitle = product.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Row(verticalAlignment = Alignment.Top) {
            ProductHeader(
                title = product.title,
                brand = product.brand,
                category = product.category,
                modifier = Modifier.weight(1f)
            )
            FavoriteIconButton(
                isFavorite = isFavorite,
                onClick = onFavoriteClick
            )
        }

        ProductPriceCard(
            price = product.price,
            discountPercentage = product.discountPercentage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        ProductRating(
            rating = product.rating,
            modifier = Modifier.padding(top = 16.dp)
        )

        ProductAvailabilityCard(
            stock = product.stock,
            shippingInformation = product.shippingInformation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        ProductDescription(
            description = product.description,
            modifier = Modifier.padding(top = 16.dp)
        )

        ProductWarrantyField(
            warrantyInformation = product.warrantyInformation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        if (quantityInCart > 0) {
            Text(
                text = "Déjà $quantityInCart dans votre panier",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        AddToCartButton(
            onClick = onAddToCartClick,
            enabled = canAddToCart,
            text = when {
                !product.isInStock -> "Indisponible"
                !canAddToCart -> "Stock maximum atteint"
                else -> "Ajouter au panier"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
    }
}

@Composable
fun ProductImage(
    productTitle: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = android.R.drawable.ic_menu_gallery),
        contentDescription = "Image du produit $productTitle",
        modifier = modifier
    )
}

@Composable
fun ProductHeader(
    title: String,
    brand: String,
    category: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = brand,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = category,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ProductPriceCard(
    price: Double,
    discountPercentage: Double,
    modifier: Modifier = Modifier
) {
    val savedAmount = price * discountPercentage / 100

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            PriceText(
                price = price,
                discountPercentage = discountPercentage
            )
            if (discountPercentage > 0) {
                Text(
                    text = "Remise : ${discountPercentage.toDiscountText()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Vous économisez ${savedAmount.toPriceText()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Text(
                    text = "Pas de remise sur ce produit",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ProductRating(
    rating: Double,
    modifier: Modifier = Modifier
) {
    val filledStars = rating.roundToInt()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (index < filledStars) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
        Text(
            text = "$rating / 5",
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ProductAvailabilityCard(
    stock: Int,
    shippingInformation: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Disponibilité",
                style = MaterialTheme.typography.titleMedium
            )
            StockLabel(stock = stock)
            Text(
                text = shippingInformation,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ProductDescription(
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Description",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ProductWarrantyField(
    warrantyInformation: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = warrantyInformation,
        onValueChange = {},
        readOnly = true,
        label = {
            Text(text = "Garantie")
        },
        modifier = modifier
    )
}

@Composable
fun AddToCartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Ajouter au panier"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

// ---------------------------------------------------------------------------
// Écran panier
// ---------------------------------------------------------------------------

@Composable
fun CartScreen(
    cartLines: List<CartLineUi>,
    cartTotal: Double,
    onIncreaseClick: (Int) -> Unit,
    onDecreaseClick: (Int) -> Unit,
    onRemoveClick: (Int) -> Unit,
    onProductClick: (ProductUi) -> Unit,
    onBrowseCatalogClick: () -> Unit,
    onCheckoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cartLines.isEmpty()) {
        EmptyStateMessage(
            title = "Votre panier est vide",
            message = "Ajoutez des produits depuis le catalogue.",
            actionLabel = "Découvrir le catalogue",
            onActionClick = onBrowseCatalogClick,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = cartLines,
            key = { line -> line.product.id }
        ) { line ->
            CartLineItem(
                line = line,
                onIncreaseClick = { onIncreaseClick(line.product.id) },
                onDecreaseClick = { onDecreaseClick(line.product.id) },
                onRemoveClick = { onRemoveClick(line.product.id) },
                onClick = { onProductClick(line.product) }
            )
        }

        item {
            CartSummary(
                itemCount = cartLines.sumOf { it.quantity },
                cartTotal = cartTotal,
                onCheckoutClick = onCheckoutClick
            )
        }
    }
}

@Composable
fun CartLineItem(
    line: CartLineUi,
    onIncreaseClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.product.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${line.product.finalPrice.toPriceText()} l’unité",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Retirer ${line.product.title} du panier"
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onDecreaseClick) {
                    Text(text = "−")
                }
                Text(
                    text = "${line.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OutlinedButton(
                    onClick = onIncreaseClick,
                    enabled = line.quantity < line.product.stock
                ) {
                    Text(text = "+")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = line.totalPrice.toPriceText(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CartSummary(
    itemCount: Int,
    cartTotal: Double,
    onCheckoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    text = "Total ($itemCount article(s))",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = cartTotal.toPriceText(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCheckoutClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Valider la commande")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
fun ProductDetailScreenPreview() {
    ProductExplorerTheme {
        ProductDetailScreen(
            product = sampleProduct(),
            isFavorite = true,
            onFavoriteClick = {},
            quantityInCart = 1,
            onAddToCartClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProductDetailScreenOutOfStockPreview() {
    ProductExplorerTheme {
        ProductDetailScreen(
            product = sampleProductOutOfStock(),
            isFavorite = false,
            onFavoriteClick = {},
            quantityInCart = 0,
            onAddToCartClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProductHomeScreenPreview() {
    ProductExplorerTheme {
        ProductHomeScreen(
            featuredProduct = sampleProduct(),
            categories = sampleCategories(),
            onFeaturedProductClick = {},
            onSearchClick = {},
            onCategoryClick = {},
            onSeeCatalogClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Catalogue - Light Theme"
)
@Composable
fun ProductCatalogScreenLightPreview() {
    ProductExplorerTheme(darkTheme = false) {
        ProductCatalogScreen(
            products = sampleProducts(),
            categories = sampleCategories(),
            searchQuery = "",
            onSearchQueryChange = {},
            showOnlyInStock = false,
            onToggleStockFilter = {},
            showOnlyFavorites = false,
            onToggleFavoritesFilter = {},
            selectedCategory = null,
            onCategoryClick = {},
            sortOption = SortOption.DEFAULT,
            onSortOptionChange = {},
            hasActiveFilters = false,
            onResetFilters = {},
            favoriteProductIds = listOf(1),
            onFavoriteClick = {},
            onProductClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Catalogue - Dark Theme"
)
@Composable
fun ProductCatalogScreenDarkPreview() {
    ProductExplorerTheme(darkTheme = true) {
        ProductCatalogScreen(
            products = sampleProducts().filter { it.category == "Audio" && it.isInStock },
            categories = sampleCategories(),
            searchQuery = "audio",
            onSearchQueryChange = {},
            showOnlyInStock = true,
            onToggleStockFilter = {},
            showOnlyFavorites = false,
            onToggleFavoritesFilter = {},
            selectedCategory = "Audio",
            onCategoryClick = {},
            sortOption = SortOption.PRICE_ASC,
            onSortOptionChange = {},
            hasActiveFilters = true,
            onResetFilters = {},
            favoriteProductIds = listOf(2),
            onFavoriteClick = {},
            onProductClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Catalogue - Aucun résultat")
@Composable
fun ProductCatalogScreenEmptyPreview() {
    ProductExplorerTheme {
        ProductCatalogScreen(
            products = emptyList(),
            categories = sampleCategories(),
            searchQuery = "télévision",
            onSearchQueryChange = {},
            showOnlyInStock = false,
            onToggleStockFilter = {},
            showOnlyFavorites = false,
            onToggleFavoritesFilter = {},
            selectedCategory = null,
            onCategoryClick = {},
            sortOption = SortOption.DEFAULT,
            onSortOptionChange = {},
            hasActiveFilters = true,
            onResetFilters = {},
            favoriteProductIds = emptyList(),
            onFavoriteClick = {},
            onProductClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Panier")
@Composable
fun CartScreenPreview() {
    val lines = sampleCartLines()
    ProductExplorerTheme {
        CartScreen(
            cartLines = lines,
            cartTotal = lines.sumOf { it.totalPrice },
            onIncreaseClick = {},
            onDecreaseClick = {},
            onRemoveClick = {},
            onProductClick = {},
            onBrowseCatalogClick = {},
            onCheckoutClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Panier vide")
@Composable
fun CartScreenEmptyPreview() {
    ProductExplorerTheme {
        CartScreen(
            cartLines = emptyList(),
            cartTotal = 0.0,
            onIncreaseClick = {},
            onDecreaseClick = {},
            onRemoveClick = {},
            onProductClick = {},
            onBrowseCatalogClick = {},
            onCheckoutClick = {}
        )
    }
}
