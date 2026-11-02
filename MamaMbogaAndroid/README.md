# Mama Mboga Android prototype

A Kotlin, XML, Material 3 grocery shopping app for a neighborhood produce business. The prototype includes a customer storefront, local checkout, and an easy vendor catalogue and order dashboard.

## Included

- Searchable, category-filtered produce grid that adjusts from two to four columns by screen width.
- Product detail cards and a basket with quantity controls, current prices, availability checks, and total recalculation.
- Basket price-change notice when the vendor edits an item already in the basket.
- Vendor dashboard with product, availability, price-review, pending-order, and confirmed daily sales totals.
- Add and edit products, including price, name, category, description, measurement unit, photo, availability, and price-review flag.
- SQLite persistence for the catalogue and orders; SharedPreferences persistence for the basket.
- Pickup or delivery checkout, local order saving, vendor order confirmation, and WhatsApp order handoff.
- Instagram, Facebook, X, and WhatsApp links in the app bar menu.
- ViewBinding and RecyclerView throughout the app.

## Run in Android Studio

Open this folder as a project and run the `app` configuration on a device or emulator. The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 17+, Android SDK 37, and Build Tools 36.0.0. The minimum Android version is 6.0 (API 23).

The included `gradlew` / `gradlew.bat` scripts download Gradle 8.9 on first use. Android Studio may prompt you to install the Android SDK platform and Build Tools if they are missing.

## Configure Mama Mboga's contact links

Update `app/src/main/res/values/strings.xml` with the real public Instagram, Facebook, X/Twitter, and WhatsApp links. The current values are examples and the WhatsApp phone is a placeholder.

## Prototype boundaries

Product, basket, and order data are stored on the device; they are not synchronized to other phones or an online store. Checkout records the order locally and opens WhatsApp when requested. Payment, delivery fees, inventory synchronization, customer accounts, and server-side order management are not integrated. Sample catalogue entries and prices are starting data and can be edited in the Vendor tab.

Product photos can be selected from the device and remain linked after restarting the app. The catalogue also provides produce emoji fallbacks. Use Mama Mboga's real images and contact information before public release.
