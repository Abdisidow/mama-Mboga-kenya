package com.mamamboga.app.data

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unit: String,
    val priceKes: Long,
    val available: Boolean,
    val needsPriceUpdate: Boolean,
    val imageUri: String?,
    val fallbackEmoji: String,
    val updatedAt: Long
)

data class BasketLine(
    val productId: String,
    val quantity: Int,
    val lastNotifiedPriceKes: Long
)

data class DashboardSummary(
    val productCount: Int = 0,
    val availableCount: Int = 0,
    val priceReviewCount: Int = 0,
    val pendingOrderCount: Int = 0,
    val salesTodayKes: Long = 0
)
