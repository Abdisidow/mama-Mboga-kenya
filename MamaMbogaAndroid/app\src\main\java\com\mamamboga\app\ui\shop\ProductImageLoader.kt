package com.mamamboga.app.ui.shop

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.mamamboga.app.R
import com.mamamboga.app.data.Product

object ProductImageLoader {
    fun bind(image: ImageView, fallback: TextView, product: Product) {
        fallback.text = product.fallbackEmoji.ifBlank { "🛒" }
        image.setImageDrawable(null)
        val rawUri = product.imageUri
        if (rawUri.isNullOrBlank()) {
            image.visibility = View.GONE
            fallback.visibility = View.VISIBLE
            return
        }
        try {
            image.setImageURI(Uri.parse(rawUri))
            if (image.drawable == null) error("Image could not be loaded")
            image.visibility = View.VISIBLE
            fallback.visibility = View.GONE
        } catch (_: Exception) {
            image.visibility = View.GONE
            fallback.visibility = View.VISIBLE
        }
    }
}
