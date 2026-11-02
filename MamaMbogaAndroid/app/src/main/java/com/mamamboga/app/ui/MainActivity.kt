package com.mamamboga.app.ui

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.mamamboga.app.R
import com.mamamboga.app.databinding.ActivityMainBinding
import com.mamamboga.app.ui.basket.BasketFragment
import com.mamamboga.app.ui.shop.ShopFragment
import com.mamamboga.app.ui.vendor.VendorFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var model: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        model = ViewModelProvider(this)[AppViewModel::class.java]

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showScreen(item.itemId)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { item -> showScreen(item.itemId) }
        binding.topAppBar.inflateMenu(R.menu.menu_social)
        binding.topAppBar.setOnMenuItemClickListener { item ->
            val url = when (item.itemId) {
                R.id.socialInstagram -> getString(R.string.social_instagram_url)
                R.id.socialFacebook -> getString(R.string.social_facebook_url)
                R.id.socialX -> getString(R.string.social_x_url)
                R.id.socialWhatsApp -> "https://wa.me/${getString(R.string.whatsapp_phone)}?text=${Uri.encode(getString(R.string.whatsapp_hello))}"
                else -> return@setOnMenuItemClickListener false
            }
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: android.content.ActivityNotFoundException) { Snackbar.make(binding.root, R.string.whatsapp_open_failed, Snackbar.LENGTH_LONG).show() }
            true
        }

        if (savedInstanceState == null) showScreen(R.id.nav_shop)
        observeGlobalFeedback()
    }

    private fun showScreen(itemId: Int) {
        val fragment = when (itemId) {
            R.id.nav_basket -> BasketFragment()
            R.id.nav_vendor -> VendorFragment()
            else -> ShopFragment()
        }
        val tag = when (itemId) {
            R.id.nav_basket -> "basket"
            R.id.nav_vendor -> "vendor"
            else -> "shop"
        }
        if (supportFragmentManager.findFragmentByTag(tag)?.isVisible == true) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment, tag)
        }
    }

    private fun observeGlobalFeedback() {
        model.products.observe(this) { products -> model.refreshPriceNotice(products) }
        model.priceNotice.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                model.consumePriceNotice()
            }
        }
        model.feedback.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                model.consumeFeedback()
            }
        }
    }
}
