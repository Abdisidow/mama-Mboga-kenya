package com.mamamboga.app.ui.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mamamboga.app.R
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.ItemShopProductBinding

class ShopProductAdapter(
    private val onOpen: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : RecyclerView.Adapter<ShopProductAdapter.ProductViewHolder>() {
    private var items: List<Product> = emptyList()

    fun submitList(products: List<Product>) {
        items = products
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ProductViewHolder(
        ItemShopProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ProductViewHolder(private val binding: ItemShopProductBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.productName.text = product.name
            binding.productUnit.text = product.unit
            binding.productPrice.text = binding.root.context.getString(R.string.price_format, product.priceKes)
            binding.availabilityBadge.text = binding.root.context.getString(
                if (product.available) R.string.available else R.string.unavailable
            )
            binding.availabilityBadge.setTextColor(
                binding.root.context.getColor(if (product.available) R.color.forest_green else R.color.unavailable)
            )
            binding.availabilityBadge.setBackgroundResource(
                if (product.available) R.drawable.bg_available_badge else R.drawable.bg_sold_out_badge
            )
            binding.priceReviewIcon.visibility = if (product.needsPriceUpdate) android.view.View.VISIBLE else android.view.View.GONE
            ProductImageLoader.bind(binding.productImage, binding.productEmoji, product)
            binding.addButton.isEnabled = product.available
            binding.addButton.setText(if (product.available) R.string.add_to_basket else R.string.unavailable)
            binding.addButton.setOnClickListener {
                it.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80L).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    onAdd(product)
                }.start()
            }
            binding.root.setOnClickListener { onOpen(product) }
        }
    }
}
