package com.mamamboga.app.ui.vendor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mamamboga.app.R
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.DialogProductEditorBinding
import com.mamamboga.app.ui.AppViewModel
import com.mamamboga.app.ui.shop.ProductImageLoader
import java.util.UUID

class ProductEditorDialogFragment : DialogFragment() {
    private var _binding: DialogProductEditorBinding? = null
    private val binding get() = _binding!!
    private var selectedImageUri: String? = null
    private var existingProduct: Product? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && _binding != null) {
            try { requireContext().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            selectedImageUri = uri.toString()
            binding.imagePreview.setImageURI(uri)
            binding.imagePreview.visibility = View.VISIBLE
            binding.imageFallback.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getBundle(ARG_PRODUCT)?.let { values ->
            if (values.getBoolean("exists")) existingProduct = Product(
                id = values.getString("id").orEmpty(), name = values.getString("name").orEmpty(),
                description = values.getString("description").orEmpty(), category = values.getString("category").orEmpty(),
                unit = values.getString("unit").orEmpty(), priceKes = values.getLong("price"),
                available = values.getBoolean("available"), needsPriceUpdate = values.getBoolean("needsReview"),
                imageUri = values.getString("imageUri"), fallbackEmoji = values.getString("emoji").orEmpty(),
                updatedAt = values.getLong("updatedAt")
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        _binding = DialogProductEditorBinding.inflate(LayoutInflater.from(requireContext()))
        val categories = resources.getStringArray(R.array.product_categories)
        binding.productCategoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
        existingProduct?.let(::populate)
        binding.choosePhotoButton.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingProduct == null) R.string.add_product else R.string.edit_product)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
        }
        return dialog
    }

    private fun populate(product: Product) {
        binding.productNameInput.setText(product.name)
        binding.productDescriptionInput.setText(product.description)
        binding.productCategoryInput.setText(product.category, false)
        binding.productUnitInput.setText(product.unit)
        binding.productPriceInput.setText(product.priceKes.toString())
        binding.availabilitySwitch.isChecked = product.available
        binding.priceReviewSwitch.isChecked = product.needsPriceUpdate
        selectedImageUri = product.imageUri
        ProductImageLoader.bind(binding.imagePreview, binding.imageFallback, product)
    }

    private fun save() {
        val name = binding.productNameInput.text?.toString()?.trim().orEmpty()
        val description = binding.productDescriptionInput.text?.toString()?.trim().orEmpty()
        val category = binding.productCategoryInput.text?.toString()?.trim().orEmpty()
        val unit = binding.productUnitInput.text?.toString()?.trim().orEmpty()
        val priceText = binding.productPriceInput.text?.toString()?.trim().orEmpty()
        val price = priceText.toLongOrNull()
        binding.nameLayout.error = if (name.isBlank()) getString(R.string.required_field) else null
        binding.descriptionLayout.error = if (description.isBlank()) getString(R.string.required_field) else null
        binding.categoryLayout.error = if (category !in resources.getStringArray(R.array.product_categories)) getString(R.string.choose_category) else null
        binding.unitLayout.error = if (unit.isBlank()) getString(R.string.required_field) else null
        binding.priceLayout.error = when { priceText.isBlank() -> getString(R.string.required_field); price == null || price < 0 -> getString(R.string.invalid_price); else -> null }
        if (listOf(binding.nameLayout, binding.descriptionLayout, binding.categoryLayout, binding.unitLayout, binding.priceLayout).any { it.error != null }) return

        val product = Product(
            id = existingProduct?.id ?: UUID.randomUUID().toString(),
            name = name, description = description, category = category, unit = unit,
            priceKes = price!!, available = binding.availabilitySwitch.isChecked,
            needsPriceUpdate = binding.priceReviewSwitch.isChecked, imageUri = selectedImageUri,
            fallbackEmoji = existingProduct?.fallbackEmoji ?: emojiFor(category), updatedAt = System.currentTimeMillis()
        )
        val model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        model.saveProduct(product, onSaved = { dismissAllowingStateLoss() }, onError = { error ->
            if (_binding != null) Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        })
    }

    private fun emojiFor(category: String) = when (category.lowercase()) {
        "vegetables" -> "🥬"
        "fruits" -> "🍊"
        "grains" -> "🌾"
        "herbs" -> "🌿"
        "tubers" -> "🥔"
        "dairy" -> "🥛"
        else -> "🛒"
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object {
        private const val ARG_PRODUCT = "product_bundle"
        fun newInstance(product: Product?) = ProductEditorDialogFragment().apply {
            arguments = Bundle().apply { putBundle(ARG_PRODUCT, Bundle().apply {
                if (product != null) {
                    putBoolean("exists", true)
                    putString("id", product.id); putString("name", product.name); putString("description", product.description)
                    putString("category", product.category); putString("unit", product.unit); putLong("price", product.priceKes)
                    putBoolean("available", product.available); putBoolean("needsReview", product.needsPriceUpdate)
                    putString("imageUri", product.imageUri); putString("emoji", product.fallbackEmoji); putLong("updatedAt", product.updatedAt)
                }
            }) }
        }
    }
}
