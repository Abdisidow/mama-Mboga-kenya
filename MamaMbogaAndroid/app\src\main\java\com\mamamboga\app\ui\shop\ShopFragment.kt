package com.mamamboga.app.ui.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mamamboga.app.R
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.DialogProductDetailsBinding
import com.mamamboga.app.databinding.FragmentShopBinding
import com.mamamboga.app.ui.AppViewModel

class ShopFragment : Fragment() {
    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!
    private lateinit var model: AppViewModel
    private lateinit var adapter: ShopProductAdapter
    private var products: List<Product> = emptyList()
    private var selectedCategory = "All"
    private val categories = listOf("All", "Vegetables", "Fruits", "Staples", "Other")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentShopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        adapter = ShopProductAdapter(::showProductDetails) { product ->
            model.addToBasket(product.id)
        }
        binding.productRecycler.layoutManager = GridLayoutManager(requireContext(), responsiveColumnCount())
        binding.productRecycler.adapter = adapter
        binding.searchInput.doAfterTextChanged { renderProducts() }
        buildCategoryChips()
        model.products.observe(viewLifecycleOwner) { updated ->
            products = updated
            binding.loadingIndicator.visibility = View.GONE
            renderProducts()
        }
        model.loading.observe(viewLifecycleOwner) { loading ->
            binding.loadingIndicator.visibility = if (loading == true) View.VISIBLE else View.GONE
        }
    }

    private fun responsiveColumnCount(): Int {
        val width = resources.configuration.screenWidthDp
        return when {
            width >= 900 -> 4
            width >= 600 -> 3
            else -> 2
        }
    }

    private fun buildCategoryChips() {
        binding.categoryChips.removeAllViews()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = categoryLabel(category)
                isCheckable = true
                isChecked = category == selectedCategory
                setTextColor(requireContext().getColorStateList(R.color.chip_text_tint))
                chipBackgroundColor = requireContext().getColorStateList(R.color.chip_background_tint)
                chipCornerRadius = resources.getDimension(R.dimen.chip_radius)
                setOnClickListener {
                    selectedCategory = category
                    renderProducts()
                }
            }
            binding.categoryChips.addView(chip)
        }
    }

    private fun categoryLabel(value: String): String = when (value) {
        "All" -> getString(R.string.category_all)
        "Staples" -> getString(R.string.category_staples)
        else -> value
    }

    private fun renderProducts() {
        if (_binding == null) return
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val filtered = products.filter { product ->
            val matchesCategory = selectedCategory == "All" || product.category.equals(selectedCategory, true)
            val matchesSearch = query.isBlank() || listOf(
                product.name,
                product.description,
                product.category,
                product.unit
            ).any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty() && binding.loadingIndicator.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        binding.productRecycler.visibility = if (filtered.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.listTitle.text = if (selectedCategory == "All") getString(R.string.popular_today) else categoryLabel(selectedCategory)
    }

    private fun showProductDetails(product: Product) {
        val details = DialogProductDetailsBinding.inflate(layoutInflater)
        details.productName.text = product.name
        details.productDescription.text = product.description
        details.productUnit.text = product.unit
        details.productPrice.text = getString(R.string.price_format, product.priceKes)
        details.availability.text = getString(if (product.available) R.string.available else R.string.unavailable)
        details.availability.setTextColor(
            requireContext().getColor(if (product.available) R.color.forest_green else R.color.unavailable)
        )
        ProductImageLoader.bind(details.productImage, details.productEmoji, product)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(details.root)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.add_to_basket, null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            button.isEnabled = product.available
            if (!product.available) button.setText(R.string.unavailable)
            button.setOnClickListener {
                model.addToBasket(product.id)
                Snackbar.make(binding.root, getString(R.string.added_to_basket), Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        binding.productRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
