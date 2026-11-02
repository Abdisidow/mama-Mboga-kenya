package com.mamamboga.app.ui.basket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mamamboga.app.R
import com.mamamboga.app.data.BasketLine
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.ItemBasketBinding
import com.mamamboga.app.ui.shop.ProductImageLoader

data class BasketDisplayItem(val line: BasketLine, val product: Product)

class BasketAdapter(
    private val onQuantityChange: (String, Int) -> Unit,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<BasketAdapter.BasketViewHolder>() {
    private var items: List<BasketDisplayItem> = emptyList()

    fun submitList(lines: List<BasketLine>, products: List<Product>) {
        val byId = products.associateBy(Product::id)
        items = lines.mapNotNull { line -> byId[line.productId]?.let { BasketDisplayItem(line, it) } }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = BasketViewHolder(
        ItemBasketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: BasketViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class BasketViewHolder(private val binding: ItemBasketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BasketDisplayItem) {
            val product = item.product
            val line = item.line
            ProductImageLoader.bind(binding.productImage, binding.productEmoji, product)
            binding.productName.text = product.name
            binding.productUnit.text = product.unit
            binding.linePrice.text = binding.root.context.getString(
                R.string.line_total,
                binding.root.context.getString(R.string.price_format, product.priceKes),
                line.quantity,
                binding.root.context.getString(R.string.price_format, product.priceKes * line.quantity)
            )
            binding.quantityText.text = line.quantity.toString()
            binding.increaseButton.isEnabled = product.available
            binding.increaseButton.contentDescription = binding.root.context.getString(
                R.string.add_one_to_basket,
                product.name
            )
            binding.decreaseButton.contentDescription = binding.root.context.getString(
                R.string.remove_one_from_basket,
                product.name
            )
            if (!product.available) {
                binding.productUnit.text = binding.root.context.getString(R.string.unavailable)
                binding.productUnit.setTextColor(binding.root.context.getColor(R.color.unavailable))
            }
            binding.increaseButton.setOnClickListener { onQuantityChange(product.id, 1) }
            binding.decreaseButton.setOnClickListener { onQuantityChange(product.id, -1) }
            binding.removeButton.setOnClickListener { onRemove(product.id) }
        }
    }
}
