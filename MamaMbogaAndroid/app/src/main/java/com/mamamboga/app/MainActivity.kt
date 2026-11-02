package com.mamamboga.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

private data class Produce(
    val id: String,
    val name: String,
    val unit: String,
    val price: Int,
    val emoji: String,
    val category: String,
    val note: String
)

private data class Category(val key: String, val labelRes: Int)

class MainActivity : Activity() {
    private val green by lazy { getColor(R.color.brand_green) }
    private val ink by lazy { getColor(R.color.ink) }
    private val muted by lazy { getColor(R.color.muted) }
    private val categories = listOf(
        Category("all", R.string.category_all),
        Category("vegetables", R.string.category_vegetables),
        Category("fruits", R.string.category_fruits),
        Category("staples", R.string.category_staples)
    )
    private val produce = listOf(
        Produce("sukuma", "Sukuma wiki", "1 bunch", 30, "🥬", "vegetables", "Fresh & leafy"),
        Produce("tomatoes", "Tomatoes", "1 kg", 120, "🍅", "vegetables", "Ripe and juicy"),
        Produce("avocado", "Avocado", "1 piece", 40, "🥑", "fruits", "Creamy goodness"),
        Produce("onions", "Red onions", "1 kg", 150, "🧅", "vegetables", "Everyday essential"),
        Produce("bananas", "Bananas", "1 bunch", 100, "🍌", "fruits", "Naturally sweet"),
        Produce("maize-flour", "Maize flour", "2 kg pack", 180, "🌽", "staples", "A pantry favourite")
    )
    private val basket = linkedMapOf<String, Int>()
    private var selectedCategory = "all"
    private lateinit var productList: LinearLayout
    private lateinit var categoryRow: LinearLayout
    private lateinit var cartButton: TextView
    private lateinit var bottomCart: TextView
    private lateinit var sectionTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreState(savedInstanceState)
        setContentView(R.layout.activity_main)
        productList = findViewById(R.id.productList)
        categoryRow = findViewById(R.id.categoryRow)
        cartButton = findViewById(R.id.cartButton)
        bottomCart = findViewById(R.id.bottomCart)
        sectionTitle = findViewById(R.id.sectionTitle)

        renderCategories()
        renderProducts()
        updateBasketCount()
        cartButton.setOnClickListener { showBasket() }
        bottomCart.setOnClickListener { showBasket() }
        findViewById<TextView>(R.id.whatsappBottom).setOnClickListener {
            openWhatsApp(getString(R.string.whatsapp_greeting))
        }
        renderSocials()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, selectedCategory)
        produce.forEach { item -> outState.putInt(basketStateKey(item), basket[item.id] ?: 0) }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) return
        selectedCategory = state.getString(STATE_CATEGORY, "all")
        produce.forEach { item ->
            val quantity = state.getInt(basketStateKey(item), 0)
            if (quantity > 0) basket[item.id] = quantity
        }
    }

    private fun basketStateKey(item: Produce) = "basket_${item.id}"

    private fun renderCategories() {
        categoryRow.removeAllViews()
        categories.forEach { category ->
            val isSelected = selectedCategory == category.key
            val chip = TextView(this).apply {
                text = getString(category.labelRes)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(17), dp(11), dp(17), dp(11))
                setTextColor(if (isSelected) Color.WHITE else ink)
                setBackgroundResource(if (isSelected) R.drawable.bg_green else R.drawable.bg_white_card)
                contentDescription = getString(
                    if (isSelected) R.string.category_selected_description else R.string.category_description,
                    text
                )
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
                setOnClickListener {
                    selectedCategory = category.key
                    renderCategories()
                    renderProducts()
                }
            }
            categoryRow.addView(chip)
        }
    }

    private fun renderProducts() {
        productList.removeAllViews()
        val category = categories.firstOrNull { it.key == selectedCategory }
        sectionTitle.text = if (selectedCategory == "all") getString(R.string.popular_today)
        else category?.let { getString(it.labelRes) }.orEmpty()
        val visibleProducts = produce.filter {
            selectedCategory == "all" || it.category == selectedCategory
        }
        visibleProducts.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.bg_white_card)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
            }
            val illustration = TextView(this).apply {
                text = item.emoji
                textSize = 30f
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.product_illustration_description, item.name)
                setBackgroundResource(R.drawable.bg_cream)
                layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            }
            val detail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(5), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            detail.addView(label(item.name, 15f, ink, true))
            detail.addView(label("${item.unit}  ·  ${item.note}", 11f, muted).apply {
                setPadding(0, dp(4), 0, 0)
            })

            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            right.addView(label(formatPrice(item.price), 14f, ink, true).apply {
                gravity = Gravity.END
            })
            val quantity = basket[item.id] ?: 0
            val add = TextView(this).apply {
                text = if (quantity > 0) getString(R.string.added_quantity, quantity)
                else getString(R.string.add_to_basket)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(green)
                setPadding(dp(11), dp(7), dp(11), dp(7))
                setBackgroundResource(R.drawable.bg_soft_green)
                contentDescription = getString(R.string.add_product_description, item.name)
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) }
                setOnClickListener { changeQuantity(item, 1); renderProducts() }
            }
            right.addView(add)
            card.addView(illustration)
            card.addView(detail)
            card.addView(right)
            productList.addView(card)
        }
    }

    private fun showBasket() {
        if (basket.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.empty_basket_title)
                .setMessage(R.string.empty_basket_message)
                .setPositiveButton(R.string.keep_shopping, null)
                .show()
            return
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(14))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        val totalLabel = label("", 17f, green, true).apply {
            setPadding(0, dp(12), 0, dp(8))
        }
        val delivery = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.delivery_option)
            isChecked = true
        }
        val pickup = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.pickup_option)
        }
        val options = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(delivery)
            addView(pickup)
        }
        var orderButton: Button? = null

        fun renderBasketLines() {
            content.removeAllViews()
            if (basket.isEmpty()) {
                content.addView(label(getString(R.string.empty_basket_message), 14f, muted).apply {
                    setPadding(0, dp(12), 0, dp(12))
                })
            }
            basket.forEach { (id, quantity) ->
                val item = produce.firstOrNull { it.id == id } ?: return@forEach
                content.addView(basketRow(item, quantity) {
                    changeQuantity(item, it)
                    renderProducts()
                    renderBasketLines()
                })
            }
            totalLabel.text = getString(R.string.basket_total, formatPrice(basketTotal()))
            content.addView(totalLabel)
            content.addView(label(getString(R.string.delivery_prompt), 14f, ink, true).apply {
                setPadding(0, dp(10), 0, 0)
            })
            content.addView(options)
            content.addView(label(getString(R.string.payment_note), 12f, muted).apply {
                setPadding(0, dp(8), 0, dp(4))
            })
            orderButton?.isEnabled = basket.isNotEmpty()
        }

        renderBasketLines()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.basket_title)
            .setView(scroll)
            .setNegativeButton(R.string.keep_shopping, null)
            .setPositiveButton(R.string.order_on_whatsapp, null)
            .create()
        dialog.setOnShowListener {
            orderButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            orderButton?.isEnabled = basket.isNotEmpty()
            orderButton?.setOnClickListener {
                if (basket.isEmpty()) return@setOnClickListener
                val service = if (pickup.isChecked) getString(R.string.pickup_label)
                else getString(R.string.delivery_label)
                val lines = basket.mapNotNull { (id, quantity) ->
                    val item = produce.firstOrNull { it.id == id } ?: return@mapNotNull null
                    getString(R.string.order_line, quantity, item.name, formatPrice(item.price * quantity))
                }.joinToString("\n")
                val message = getString(
                    R.string.order_message,
                    service,
                    lines,
                    formatPrice(basketTotal())
                )
                openWhatsApp(message)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun basketRow(item: Produce, quantity: Int, onQuantityChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        text.addView(label("${item.emoji}  ${item.name}", 14f, ink, true))
        text.addView(label(formatPrice(item.price * quantity), 12f, muted).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(text)
        row.addView(quantityButton("−", getString(R.string.decrease_quantity_description, item.name)) {
            onQuantityChange(-1)
        })
        row.addView(label(quantity.toString(), 14f, ink, true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(30)
        })
        row.addView(quantityButton("+", getString(R.string.increase_quantity_description, item.name)) {
            onQuantityChange(1)
        })
        return row
    }

    private fun quantityButton(text: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(green)
            contentDescription = description
            isFocusable = true
            setBackgroundResource(R.drawable.bg_soft_green)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { action() }
        }

    private fun renderSocials() {
        val row = findViewById<LinearLayout>(R.id.socialRow)
        val socials = listOf(
            R.string.social_instagram to getString(R.string.instagram_url),
            R.string.social_facebook to getString(R.string.facebook_url),
            R.string.social_x to getString(R.string.x_url),
            R.string.social_whatsapp to whatsappUrl()
        )
        socials.forEach { (labelRes, url) ->
            val socialName = getString(labelRes)
            row.addView(TextView(this).apply {
                text = socialName
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(green)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setBackgroundResource(R.drawable.bg_soft_green)
                contentDescription = getString(R.string.open_social_description, socialName)
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) }
                setOnClickListener { launchUrl(url) }
            })
        }
    }

    private fun changeQuantity(item: Produce, delta: Int) {
        val next = (basket[item.id] ?: 0) + delta
        if (next <= 0) basket.remove(item.id) else basket[item.id] = next
        updateBasketCount()
    }

    private fun basketTotal(): Int = basket.entries.sumOf { (id, quantity) ->
        (produce.firstOrNull { it.id == id }?.price ?: 0) * quantity
    }

    private fun updateBasketCount() {
        val count = basket.values.sum()
        cartButton.text = getString(R.string.cart_count, count)
        cartButton.contentDescription = getString(R.string.cart_description, count)
        bottomCart.text = if (count > 0) getString(R.string.basket_count, count)
        else getString(R.string.basket_nav)
        bottomCart.contentDescription = getString(R.string.cart_description, count)
    }

    private fun openWhatsApp(message: String) {
        launchUrl("${whatsappUrl()}?text=${Uri.encode(message)}")
    }

    private fun whatsappUrl(): String = "https://wa.me/${getString(R.string.whatsapp_phone)}"

    private fun launchUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_link_handler, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPrice(amount: Int): String = getString(R.string.price_format, amount)

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STATE_CATEGORY = "selected_category"
    }
}
