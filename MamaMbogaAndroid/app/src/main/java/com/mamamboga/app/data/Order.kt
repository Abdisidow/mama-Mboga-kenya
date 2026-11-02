package com.mamamboga.app.data

data class OrderLine(
    val productId: String,
    val productName: String,
    val unit: String,
    val unitPriceKes: Long,
    val quantity: Int
)

data class CustomerOrder(
    val id: String,
    val createdAt: Long,
    val customerName: String,
    val customerPhone: String,
    val fulfillment: String,
    val address: String,
    val totalKes: Long,
    val lines: List<OrderLine>,
    val status: String
)
