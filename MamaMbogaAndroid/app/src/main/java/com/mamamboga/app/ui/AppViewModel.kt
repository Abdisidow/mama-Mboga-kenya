package com.mamamboga.app.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mamamboga.app.data.BasketLine
import com.mamamboga.app.data.CustomerOrder
import com.mamamboga.app.data.DashboardSummary
import com.mamamboga.app.data.OrderLine
import com.mamamboga.app.data.Product
import com.mamamboga.app.data.ProductRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

sealed class OrderResult {
    data class Success(val order: CustomerOrder, val whatsappMessage: String) : OrderResult()
    data class Failure(val message: String) : OrderResult()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = application.getSharedPreferences("shopping_basket", 0)

    private val mutableProducts = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = mutableProducts
    private val mutableBasket = MutableLiveData(loadBasket())
    val basket: LiveData<List<BasketLine>> = mutableBasket
    private val mutableOrders = MutableLiveData<List<CustomerOrder>>(emptyList())
    val pendingOrders: LiveData<List<CustomerOrder>> = mutableOrders
    private val mutableDashboard = MutableLiveData(DashboardSummary())
    val dashboard: LiveData<DashboardSummary> = mutableDashboard
    private val mutableLoading = MutableLiveData(true)
    val loading: LiveData<Boolean> = mutableLoading
    private val mutableOrderResult = MutableLiveData<OrderResult?>()
    val orderResult: LiveData<OrderResult?> = mutableOrderResult
    private val mutablePlacingOrder = MutableLiveData(false)
    val placingOrder: LiveData<Boolean> = mutablePlacingOrder
    private val mutablePriceNotice = MutableLiveData<String?>()
    val priceNotice: LiveData<String?> = mutablePriceNotice
    private val mutableFeedback = MutableLiveData<String?>()
    val feedback: LiveData<String?> = mutableFeedback

    init {
        refreshAll()
    }

    fun refreshPriceNotice(currentProducts: List<Product>) {
        val byId = currentProducts.associateBy(Product::id)
        val currentLines = mutableBasket.value.orEmpty()
        val changes = mutableListOf<Pair<Product, Long>>()
        val updatedLines = currentLines.mapNotNull { line ->
            val product = byId[line.productId] ?: return@mapNotNull null
            if (product.priceKes == line.lastNotifiedPriceKes) line
            else {
                changes += product to product.priceKes
                line.copy(lastNotifiedPriceKes = product.priceKes)
            }
        }
        if (changes.isNotEmpty()) {
            setBasket(updatedLines)
            val details = changes.distinctBy { it.first.id }.joinToString { (product, price) ->
                "${product.name} is now ${priceText(price)}"
            }
            mutablePriceNotice.value = "Prices changed: $details. Your basket total has been updated."
        }
    }

    fun consumePriceNotice() {
        mutablePriceNotice.value = null
    }

    fun addToBasket(productId: String) {
        val product = mutableProducts.value.orEmpty().firstOrNull { it.id == productId }
        if (product == null) {
            mutableFeedback.value = "Product is unavailable. Refresh and try again."
            return
        }
        if (!product.available) {
            mutableFeedback.value = "${product.name} is currently unavailable."
            return
        }
        val lines = mutableBasket.value.orEmpty().toMutableList()
        val index = lines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val oldLine = lines[index]
            if (oldLine.quantity >= MAX_ITEM_QUANTITY) {
                mutableFeedback.value = "You can add up to $MAX_ITEM_QUANTITY of each item."
                return
            }
            lines[index] = oldLine.copy(quantity = oldLine.quantity + 1)
        } else {
            lines += BasketLine(productId, 1, product.priceKes)
        }
        setBasket(lines)
        mutableFeedback.value = "${product.name} added to your basket."
    }

    fun changeBasketQuantity(productId: String, delta: Int) {
        val lines = mutableBasket.value.orEmpty().mapNotNull { line ->
            if (line.productId != productId) line
            else {
                val quantity = line.quantity + delta
                when {
                    quantity <= 0 -> null
                    quantity > MAX_ITEM_QUANTITY -> {
                        mutableFeedback.value = "You can add up to $MAX_ITEM_QUANTITY of each item."
                        line
                    }
                    else -> line.copy(quantity = quantity)
                }
            }
        }
        setBasket(lines)
    }

    fun saveProduct(product: Product, onSaved: () -> Unit, onError: (String) -> Unit) {
        val validation = validateProduct(product)
        if (validation != null) {
            onError(validation)
            return
        }
        executor.execute {
            try {
                repository.saveProduct(product.copy(updatedAt = System.currentTimeMillis()))
                val updated = repository.getProducts()
                val summary = repository.getDashboardSummary()
                mainHandler.post {
                    mutableProducts.value = updated
                    mutableDashboard.value = summary
                    onSaved()
                }
            } catch (error: Exception) {
                mainHandler.post { onError(error.message ?: "Could not save this product.") }
            }
        }
    }

    fun setProductAvailability(productId: String, available: Boolean) {
        executor.execute {
            try {
                repository.setAvailability(productId, available)
                publishCatalog()
            } catch (error: Exception) {
                mainHandler.post { mutableFeedback.value = error.message ?: "Could not update availability." }
            }
        }
    }

    fun markPriceReviewed(productId: String) {
        executor.execute {
            try {
                repository.markPriceReviewed(productId)
                publishCatalog()
            } catch (error: Exception) {
                mainHandler.post { mutableFeedback.value = error.message ?: "Could not update the price review." }
            }
        }
    }

    fun placeOrder(
        customerName: String,
        customerPhone: String,
        fulfillment: String,
        address: String
    ) {
        if (mutablePlacingOrder.value == true) return
        val name = customerName.trim()
        val phone = customerPhone.trim()
        val dropOff = address.trim()
        val items = mutableBasket.value.orEmpty()
        val catalog = mutableProducts.value.orEmpty().associateBy(Product::id)
        if (items.isEmpty()) {
            mutableFeedback.value = "Add an item before placing your order."
            return
        }
        if (name.isBlank()) {
            mutableFeedback.value = "Enter your name to continue."
            return
        }
        if (!phone.matches(Regex("^\\+?[0-9]{9,15}$"))) {
            mutableFeedback.value = "Enter a valid phone number, including country code if needed."
            return
        }
        if (fulfillment == FULFILLMENT_DELIVERY && dropOff.isBlank()) {
            mutableFeedback.value = "Enter a delivery location or choose pickup."
            return
        }
        val orderLines = items.mapNotNull { line ->
            val product = catalog[line.productId] ?: return@mapNotNull null
            if (!product.available) return@mapNotNull null
            OrderLine(product.id, product.name, product.unit, product.priceKes, line.quantity)
        }
        if (orderLines.size != items.size) {
            mutableFeedback.value = "One or more basket items are unavailable. Remove them before checkout."
            return
        }
        if (orderLines.any { it.quantity !in 1..MAX_ITEM_QUANTITY }) {
            mutableFeedback.value = "Check item quantities before placing your order."
            return
        }
        val order = CustomerOrder(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            customerName = name,
            customerPhone = phone,
            fulfillment = fulfillment,
            address = if (fulfillment == FULFILLMENT_DELIVERY) dropOff else "",
            totalKes = orderLines.sumOf { it.unitPriceKes * it.quantity },
            lines = orderLines,
            status = ProductRepository.ORDER_PENDING
        )
        mutablePlacingOrder.value = true
        executor.execute {
            try {
                repository.saveOrder(order)
                val message = order.toWhatsAppMessage()
                val refreshedOrders = repository.getPendingOrders()
                val summary = repository.getDashboardSummary()
                mainHandler.post {
                    setBasket(emptyList())
                    mutableOrders.value = refreshedOrders
                    mutableDashboard.value = summary
                    mutablePlacingOrder.value = false
                    mutableOrderResult.value = OrderResult.Success(order, message)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    mutablePlacingOrder.value = false
                    mutableOrderResult.value = OrderResult.Failure(
                        error.message ?: "We couldn’t save your order. Please try again."
                    )
                }
            }
        }
    }

    fun consumeOrderResult() {
        mutableOrderResult.value = null
    }

    fun confirmOrder(orderId: String) {
        executor.execute {
            try {
                repository.confirmOrder(orderId)
                val orders = repository.getPendingOrders()
                val summary = repository.getDashboardSummary()
                mainHandler.post {
                    mutableOrders.value = orders
                    mutableDashboard.value = summary
                    mutableFeedback.value = "Order confirmed."
                }
            } catch (error: Exception) {
                mainHandler.post { mutableFeedback.value = error.message ?: "Could not confirm the order." }
            }
        }
    }

    fun consumeFeedback() {
        mutableFeedback.value = null
    }

    private fun validateProduct(product: Product): String? = when {
        product.name.isBlank() -> "Enter a product name."
        product.name.length > 60 -> "Keep the product name under 60 characters."
        product.description.isBlank() -> "Enter a short product description."
        product.category.isBlank() -> "Choose a product category."
        product.unit.isBlank() -> "Enter a measurement unit."
        product.priceKes < 0 -> "Price cannot be negative."
        product.priceKes > MAX_PRICE_KES -> "Enter a valid price."
        else -> null
    }

    private fun refreshAll() {
        executor.execute {
            try {
                val catalog = repository.getProducts()
                val orders = repository.getPendingOrders()
                val summary = repository.getDashboardSummary()
                mainHandler.post {
                    mutableProducts.value = catalog
                    mutableOrders.value = orders
                    mutableDashboard.value = summary
                    mutableLoading.value = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    mutableLoading.value = false
                    mutableFeedback.value = error.message ?: "Could not load the local product catalogue."
                }
            }
        }
    }

    private fun publishCatalog() {
        val catalog = repository.getProducts()
        val summary = repository.getDashboardSummary()
        mainHandler.post {
            mutableProducts.value = catalog
            mutableDashboard.value = summary
        }
    }

    private fun setBasket(lines: List<BasketLine>) {
        mutableBasket.value = lines
        preferences.edit().putString(BASKET_JSON_KEY, lines.toJson().toString()).apply()
    }

    private fun loadBasket(): List<BasketLine> = try {
        val json = JSONArray(preferences.getString(BASKET_JSON_KEY, "[]"))
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val quantity = item.optInt("quantity", 0)
                if (quantity in 1..MAX_ITEM_QUANTITY) {
                    add(
                        BasketLine(
                            productId = item.getString("productId"),
                            quantity = quantity,
                            lastNotifiedPriceKes = item.optLong("lastNotifiedPriceKes", 0)
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun List<BasketLine>.toJson() = JSONArray().apply {
        forEach { line ->
            put(
                JSONObject()
                    .put("productId", line.productId)
                    .put("quantity", line.quantity)
                    .put("lastNotifiedPriceKes", line.lastNotifiedPriceKes)
            )
        }
    }

    private fun CustomerOrder.toWhatsAppMessage(): String = buildString {
        appendLine("Hi Mama Mboga! I’d like to place an order.")
        appendLine("Name: $customerName")
        appendLine("Phone: $customerPhone")
        appendLine("Fulfilment: $fulfillment")
        if (address.isNotBlank()) appendLine("Delivery location: $address")
        lines.forEach { line ->
            appendLine("${line.quantity} × ${line.productName} (${line.unit}) — ${priceText(line.unitPriceKes * line.quantity)}")
        }
        append("Total: ${priceText(totalKes)}")
    }

    private fun priceText(amountKes: Long) = "KSh $amountKes"

    override fun onCleared() {
        executor.shutdown()
        repository.close()
        super.onCleared()
    }

    companion object {
        const val FULFILLMENT_DELIVERY = "Delivery"
        const val FULFILLMENT_PICKUP = "Pickup"
        private const val BASKET_JSON_KEY = "basket_lines"
        private const val MAX_ITEM_QUANTITY = 99
        private const val MAX_PRICE_KES = 100_000_000L
    }
}
