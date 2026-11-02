package com.mamamboga.app.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mamamboga.app.R
import com.mamamboga.app.data.DashboardSummary
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.FragmentVendorBinding
import com.mamamboga.app.ui.AppViewModel
import com.google.android.material.snackbar.Snackbar

class VendorFragment : Fragment() {
    private var _binding: FragmentVendorBinding? = null
    private val binding get() = _binding!!
    private lateinit var model: AppViewModel
    private lateinit var productsAdapter: VendorProductAdapter
    private lateinit var ordersAdapter: VendorOrderAdapter
    private var products: List<Product> = emptyList()
    private var query = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentVendorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        productsAdapter = VendorProductAdapter(::editProduct) { product, available ->
            model.setProductAvailability(product.id, available)
        }
        ordersAdapter = VendorOrderAdapter { model.confirmOrder(it.id) }
        binding.vendorProductRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.vendorProductRecycler.adapter = productsAdapter
        binding.orderRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.orderRecycler.adapter = ordersAdapter
        binding.addProductButton.setOnClickListener { editProduct(null) }
        binding.productSearchInput.doAfterTextChanged { query = it?.toString().orEmpty(); renderProducts() }
        model.products.observe(viewLifecycleOwner) { products = it; renderProducts() }
        model.pendingOrders.observe(viewLifecycleOwner) { orders ->
            ordersAdapter.submitList(orders)
            binding.ordersEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }
        model.dashboard.observe(viewLifecycleOwner) { renderDashboard(it) }
        model.feedback.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) { Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show(); model.consumeFeedback() }
        }
    }

    private fun renderDashboard(summary: DashboardSummary) {
        binding.productCount.text = summary.productCount.toString()
        binding.availableCount.text = summary.availableCount.toString()
        binding.priceReviewCount.text = summary.priceReviewCount.toString()
        binding.pendingOrderCount.text = summary.pendingOrderCount.toString()
        binding.dailySales.text = getString(R.string.price_format, summary.salesTodayKes)
    }

    private fun renderProducts() {
        if (_binding == null) return
        val shown = products.filter { it.name.contains(query, true) || it.category.contains(query, true) }
        productsAdapter.submitList(shown)
        binding.productsEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun editProduct(product: Product?) {
        ProductEditorDialogFragment.newInstance(product).show(childFragmentManager, "product-editor")
    }

    override fun onDestroyView() {
        binding.vendorProductRecycler.adapter = null
        binding.orderRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
