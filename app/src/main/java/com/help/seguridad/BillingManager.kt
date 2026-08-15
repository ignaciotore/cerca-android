package com.help.seguridad

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.security.MessageDigest

class BillingManager(
    private val activity: Activity,
    private val onEntitlementChanged: (Boolean) -> Unit,
    private val onPurchaseTokenAvailable: (String) -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val SUBSCRIPTION_PRODUCT_ID = "help_monthly"
        private const val PREFS = "help_prefs"

        fun obfuscateAccountId(userId: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(userId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    private lateinit var billingClient: BillingClient
    private var billingReady = false
    private var subscriptionProductDetails: ProductDetails? = null

    fun start() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (billingReady) {
                    querySubscriptionProduct()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
            }
        })
    }

    fun close() {
        if (::billingClient.isInitialized) billingClient.endConnection()
    }

    fun refreshPurchases() {
        if (!::billingClient.isInitialized || !billingReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun querySubscriptionProduct(onReady: (() -> Unit)? = null) {
        if (!billingReady) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SUBSCRIPTION_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subscriptionProductDetails = result.productDetailsList.firstOrNull()
                if (subscriptionProductDetails != null) onReady?.invoke()
            }
        }
    }

    fun launchSubscription(obfuscatedAccountId: String) {
        if (!billingReady) {
            onMessage("Google Play todavía no está listo. Probá nuevamente en unos segundos.")
            return
        }

        val details = subscriptionProductDetails
        if (details == null) {
            querySubscriptionProduct { launchSubscription(obfuscatedAccountId) }
            onMessage("Preparando la suscripción…")
            return
        }

        val offers = details.subscriptionOfferDetails.orEmpty()
        val offer = offers.firstOrNull { "trial30" in it.offerTags } ?: offers.firstOrNull()
        if (offer == null) {
            onMessage("La suscripción todavía no está configurada en Google Play.")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("No pude abrir la compra en Google Play.")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> onMessage("Compra cancelada.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> onMessage("Google Play no pudo completar la operación.")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val relevant = purchases.filter { it.products.contains(SUBSCRIPTION_PRODUCT_ID) }
        val active = relevant.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("subscriptionActive", active)
            .apply()

        relevant
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase -> onPurchaseTokenAvailable(purchase.purchaseToken) }

        relevant
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { }
            }

        onEntitlementChanged(active)
    }
}
