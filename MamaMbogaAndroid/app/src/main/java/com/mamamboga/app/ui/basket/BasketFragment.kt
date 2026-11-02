package com.mamamboga.app.ui.basket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mamamboga.app.R
import com.mamamboga.app.data.BasketLine
import com.mamamboga.app.data.Product
import com.mamamboga.app.databinding.FragmentBasketBinding
import com.mamamboga.app.ui.AppViewModel
import com.mamamboga.app.ui.OrderResult

class BasketFragment : Fragment() {
    private var _binding: FragmentBasketBinding? = null
    private val binding get() = _binding!!
    private lateinit var model: AppViewModel
    private lateinit var adapter: BasketAdapter
    private var currentBasket: List<BasketLine> = emptyList()
    private var currentProducts: List<Product> = emptyList()
    private var whatsappAfterOrder = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentBasketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        adapter = BasketAdapter(model::changeBasketQuantity) { model.changeBasketQuantity(it, -99) }
        binding.basketRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.basketRecycler.adapter = adapter

        binding.fulfillmentGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.addressLayout.visibility = if (checkedId == R.id.deliveryRadio) View.VISIBLE else View.GONE
            updateCheckout()
        }
        binding.customerNameInput.doAfterTextChanged { updateCheckout() }
        binding.customerPhoneInput.doAfterTextChanged { updateCheckout() }
        binding.deliveryAddressInput.doAfterTextChanged { updateCheckout() }
        binding.placeOrderButton.setOnClickListener { submitOrder(openWhatsApp = false) }
        binding.whatsappOrderButton.setOnClickListener { submitOrder(openWhatsApp = true) }

        model.products.observe(viewLifecycleOwner) {
            currentProducts = it
            renderBasket()
        }
        model.basket.observe(viewLifecycleOwner) {
            currentBasket = it
            renderBasket()
        }
        model.placingOrder.observe(viewLifecycleOwner) { busy ->
            binding.orderProgress.visibility = if (busy == true) View.VISIBLE else View.GONE
            binding.placeOrderButton.isEnabled = busy != true && canPlaceOrder()
            binding.whatsappOrderButton.isEnabled = busy != true && canPlaceOrder()
        }
        model.orderResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OrderResult.Success -> {
                    Snackbar.make(binding.root, R.string.order_saved, Snackbar.LENGTH_LONG).show()
                    if (whatsappAfterOrder) openWhatsApp(result.whatsappMessage)
                    whatsappAfterOrder = false
                    model.consumeOrderResult()
                }
                is OrderResult.Failure -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    model.consumeOrderResult()
                    updateCheckout()
                }
                null -> Unit
            }
        }
        updateCheckout()
    }

    private fun renderBasket() {
        if (_binding == null) return
        adapter.submitList(currentBasket, currentProducts)
        val available = currentProducts.associateBy(Product::id)
        val validLines = currentBasket.mapNotNull { line -> available[line.productId]?.let { line to it } }
        val isEmpty = validLines.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.basketRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.checkoutForm.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.totalCard.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.placeOrderButton.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.whatsappOrderButton.visibility = if (isEmpty) View.GONE else View.VISIBLE
        val total = validLines.sumOf { (line, product) -> product.priceKes * line.quantity }
        binding.totalAmount.text = getString(R.string.price_format, total)
        val hasUnavailable = validLines.any { (_, product) -> !product.available }
        binding.unavailableNotice.visibility = if (hasUnavailable) View.VISIBLE else View.GONE
        updateCheckout()
    }

    private fun canPlaceOrder(): Boolean {
        val validLines = currentBasket.mapNotNull { line ->
            currentProducts.firstOrNull { it.id == line.productId }?.let { line to it }
        }
        val hasItems = validLines.isNotEmpty() && validLines.all { (line, product) ->
            line.quantity > 0 && product.available
        }
        val nameValid = binding.customerNameInput.text?.toString()?.trim()?.isNotEmpty() == true
        val phone = binding.customerPhoneInput.text?.toString()?.trim().orEmpty()
        val phoneValid = phone.matches(Regex("^\\+?[0-9]{9,15}$"))
        val deliverySelected = binding.fulfillmentGroup.checkedRadioButtonId == R.id.deliveryRadio
        val addressValid = !deliverySelected || binding.deliveryAddressInput.text?.toString()?.trim()?.isNotEmpty() == true
        return hasItems && nameValid && phoneValid && addressValid
    }

    private fun updateCheckout() {
        if (_binding == null) return
        val phone = binding.customerPhoneInput.text?.toString()?.trim().orEmpty()
        binding.phoneLayout.error = if (phone.isNotEmpty() && !phone.matches(Regex("^\\+?[0-9]{9,15}$"))) {
            getString(R.string.invalid_customer_phone)
        } else null
        binding.nameLayout.error = null
        binding.addressLayout.error = null
        binding.placeOrderButton.isEnabled = model.placingOrder.value != true && canPlaceOrder()
        binding.whatsappOrderButton.isEnabled = model.placingOrder.value != true && canPlaceOrder()
    }

    private fun submitOrder(openWhatsApp: Boolean) {
        if (!canPlaceOrder() || model.placingOrder.value == true) return
        whatsappAfterOrder = openWhatsApp
        val fulfillment = if (binding.fulfillmentGroup.checkedRadioButtonId == R.id.pickupRadio) {
            AppViewModel.FULFILLMENT_PICKUP
        } else AppViewModel.FULFILLMENT_DELIVERY
        model.placeOrder(
            customerName = binding.customerNameInput.text?.toString().orEmpty(),
            customerPhone = binding.customerPhoneInput.text?.toString().orEmpty(),
            fulfillment = fulfillment,
            address = binding.deliveryAddressInput.text?.toString().orEmpty()
        )
    }

    private fun openWhatsApp(message: String) {
        val phone = getString(R.string.whatsapp_phone)
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.whatsapp_open_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        binding.basketRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
