package com.mamamboga.app.ui.vendor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mamamboga.app.R
import com.mamamboga.app.data.CustomerOrder
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.ItemVendorOrderBinding
import com.mamamboga.app.databinding.ItemVendorProductBinding
import com.mamamboga.app.ui.shop.ProductImageLoader

class VendorProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onAvailability: (Product, Boolean) -> Unit
) : RecyclerView.Adapter<VendorProductAdapter.Holder>() {
    private var items: List<Product> = emptyList()
    fun submitList(products: List<Product>) { items = products; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemVendorProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    inner class Holder(private val b: ItemVendorProductBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(product: Product) {
            b.productName.text = product.name
            b.productPrice.text = b.root.context.getString(R.string.price_with_unit, product.priceKes, product.unit)
            b.priceReviewLabel.visibility = if (product.needsPriceUpdate) View.VISIBLE else View.GONE
            ProductImageLoader.bind(b.productImage, b.productEmoji, product)
            b.availabilitySwitch.setOnCheckedChangeListener(null)
            b.availabilitySwitch.isChecked = product.available
            b.availabilitySwitch.setOnCheckedChangeListener { _, checked -> onAvailability(product, checked) }
            b.editPriceButton.setOnClickListener { onEdit(product) }
            b.root.setOnClickListener { onEdit(product) }
        }
    }
}

class VendorOrderAdapter(private val onConfirm: (CustomerOrder) -> Unit) : RecyclerView.Adapter<VendorOrderAdapter.Holder>() {
    private var items: List<CustomerOrder> = emptyList()
    fun submitList(orders: List<CustomerOrder>) { items = orders; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemVendorOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    inner class Holder(private val b: ItemVendorOrderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(order: CustomerOrder) {
            b.customerName.text = order.customerName
            b.orderMetadata.text = "${order.customerPhone} · ${order.fulfillment}" + if (order.address.isBlank()) "" else " · ${order.address}"
            b.orderItems.text = order.lines.joinToString { "${it.quantity} × ${it.productName}" }
            b.orderTotal.text = b.root.context.getString(R.string.price_format, order.totalKes)
            b.confirmOrderButton.setOnClickListener { onConfirm(order) }
        }
    }
}
