package com.mamamboga.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class ProductRepository(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE products (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                unit TEXT NOT NULL,
                price_kes INTEGER NOT NULL CHECK(price_kes >= 0),
                available INTEGER NOT NULL DEFAULT 1,
                needs_price_update INTEGER NOT NULL DEFAULT 0,
                image_uri TEXT,
                fallback_emoji TEXT NOT NULL DEFAULT '🛒',
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE orders (
                id TEXT PRIMARY KEY NOT NULL,
                created_at INTEGER NOT NULL,
                customer_name TEXT NOT NULL,
                customer_phone TEXT NOT NULL,
                fulfillment TEXT NOT NULL,
                address TEXT NOT NULL,
                total_kes INTEGER NOT NULL CHECK(total_kes >= 0),
                lines_json TEXT NOT NULL,
                status TEXT NOT NULL
            )""".trimIndent()
        )
        seedProducts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Future schema changes belong here; version 1 data is retained.
        }
    }

    @Synchronized
    fun getProducts(): List<Product> = readableDatabase.query(
        TABLE_PRODUCTS,
        null,
        null,
        null,
        null,
        null,
        "available DESC, name COLLATE NOCASE ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProduct()) } }

    @Synchronized
    fun saveProduct(product: Product) {
        require(product.name.isNotBlank()) { "Enter a product name." }
        require(product.description.isNotBlank()) { "Enter a product description." }
        require(product.category.isNotBlank()) { "Choose a category." }
        require(product.unit.isNotBlank()) { "Enter a measurement unit." }
        require(product.priceKes >= 0) { "Price cannot be negative." }
        val values = product.toContentValues()
        writableDatabase.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun setAvailability(productId: String, available: Boolean) {
        writableDatabase.update(
            TABLE_PRODUCTS,
            ContentValues().apply { put("available", if (available) 1 else 0) },
            "id = ?",
            arrayOf(productId)
        )
    }

    @Synchronized
    fun markPriceReviewed(productId: String) {
        writableDatabase.update(
            TABLE_PRODUCTS,
            ContentValues().apply { put("needs_price_update", 0); put("updated_at", System.currentTimeMillis()) },
            "id = ?",
            arrayOf(productId)
        )
    }

    @Synchronized
    fun getPendingOrders(): List<CustomerOrder> = readableDatabase.query(
        TABLE_ORDERS,
        null,
        "status = ?",
        arrayOf(ORDER_PENDING),
        null,
        null,
        "created_at ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toOrder()) } }

    @Synchronized
    fun saveOrder(order: CustomerOrder) {
        val values = ContentValues().apply {
            put("id", order.id)
            put("created_at", order.createdAt)
            put("customer_name", order.customerName)
            put("customer_phone", order.customerPhone)
            put("fulfillment", order.fulfillment)
            put("address", order.address)
            put("total_kes", order.totalKes)
            put("lines_json", order.lines.toJson().toString())
            put("status", ORDER_PENDING)
        }
        writableDatabase.insertOrThrow(TABLE_ORDERS, null, values)
    }

    @Synchronized
    fun confirmOrder(orderId: String) {
        writableDatabase.update(
            TABLE_ORDERS,
            ContentValues().apply { put("status", ORDER_CONFIRMED) },
            "id = ? AND status = ?",
            arrayOf(orderId, ORDER_PENDING)
        )
    }

    @Synchronized
    fun getDashboardSummary(): DashboardSummary {
        val productSummary = readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(available), 0), COALESCE(SUM(needs_price_update), 0) FROM products",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
            else Triple(0, 0, 0)
        }
        val pending = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM orders WHERE status = ?",
            arrayOf(ORDER_PENDING)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sales = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(total_kes), 0) FROM orders WHERE status = ? AND created_at >= ?",
            arrayOf(ORDER_CONFIRMED, startOfDay.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        return DashboardSummary(productSummary.first, productSummary.second, productSummary.third, pending, sales)
    }

    private fun Cursor.toProduct() = Product(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        description = getString(getColumnIndexOrThrow("description")),
        category = getString(getColumnIndexOrThrow("category")),
        unit = getString(getColumnIndexOrThrow("unit")),
        priceKes = getLong(getColumnIndexOrThrow("price_kes")),
        available = getInt(getColumnIndexOrThrow("available")) == 1,
        needsPriceUpdate = getInt(getColumnIndexOrThrow("needs_price_update")) == 1,
        imageUri = getString(getColumnIndexOrThrow("image_uri")),
        fallbackEmoji = getString(getColumnIndexOrThrow("fallback_emoji")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
    )

    private fun Cursor.toOrder(): CustomerOrder {
        val json = JSONArray(getString(getColumnIndexOrThrow("lines_json")))
        val lines = buildList {
            for (index in 0 until json.length()) {
                val line = json.getJSONObject(index)
                add(
                    OrderLine(
                        productId = line.getString("productId"),
                        productName = line.getString("productName"),
                        unit = line.getString("unit"),
                        unitPriceKes = line.getLong("unitPriceKes"),
                        quantity = line.getInt("quantity")
                    )
                )
            }
        }
        return CustomerOrder(
            id = getString(getColumnIndexOrThrow("id")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            customerName = getString(getColumnIndexOrThrow("customer_name")),
            customerPhone = getString(getColumnIndexOrThrow("customer_phone")),
            fulfillment = getString(getColumnIndexOrThrow("fulfillment")),
            address = getString(getColumnIndexOrThrow("address")),
            totalKes = getLong(getColumnIndexOrThrow("total_kes")),
            lines = lines,
            status = getString(getColumnIndexOrThrow("status"))
        )
    }

    private fun Product.toContentValues() = ContentValues().apply {
        put("id", id)
        put("name", name.trim())
        put("description", description.trim())
        put("category", category.trim())
        put("unit", unit.trim())
        put("price_kes", priceKes)
        put("available", if (available) 1 else 0)
        put("needs_price_update", if (needsPriceUpdate) 1 else 0)
        put("image_uri", imageUri)
        put("fallback_emoji", fallbackEmoji)
        put("updated_at", System.currentTimeMillis())
    }

    private fun List<OrderLine>.toJson() = JSONArray().apply {
        forEach { line ->
            put(
                JSONObject()
                    .put("productId", line.productId)
                    .put("productName", line.productName)
                    .put("unit", line.unit)
                    .put("unitPriceKes", line.unitPriceKes)
                    .put("quantity", line.quantity)
            )
        }
    }

    private fun seedProducts(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val samples = listOf(
            Product("sukuma", "Sukuma wiki", "Fresh, leafy greens for everyday meals.", "Vegetables", "1 bunch", 30, true, false, null, "🥬", now),
            Product("tomatoes", "Tomatoes", "Ripe and juicy market tomatoes.", "Vegetables", "1 kg", 120, true, true, null, "🍅", now),
            Product("avocado", "Avocado", "Creamy, ready-to-eat avocado.", "Fruits", "1 piece", 40, true, false, null, "🥑", now),
            Product("onions", "Red onions", "A kitchen essential with a rich flavour.", "Vegetables", "1 kg", 150, true, false, null, "🧅", now),
            Product("bananas", "Bananas", "Naturally sweet bananas for the family.", "Fruits", "1 bunch", 100, true, false, null, "🍌", now),
            Product("maize-flour", "Maize flour", "A pantry favourite for ugali.", "Staples", "2 kg pack", 180, true, false, null, "🌽", now)
        )
        samples.forEach { product ->
            db.insertOrThrow(TABLE_PRODUCTS, null, product.toContentValues())
        }
    }

    companion object {
        private const val DATABASE_NAME = "mama_mboga.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_PRODUCTS = "products"
        private const val TABLE_ORDERS = "orders"
        const val ORDER_PENDING = "PENDING"
        const val ORDER_CONFIRMED = "CONFIRMED"
    }
}
