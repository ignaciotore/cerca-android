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
                    querySubscriptionProduct(reportErrors = false)
                    refreshPurchases()
                } else {
                    activity.runOnUiThread {
                        onMessage("No pude conectar con Google Play Billing: ${billingResult.responseCode} · ${billingResult.debugMessage}")
                    }
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

    private fun querySubscriptionProduct(
        reportErrors: Boolean,
        onReady: ((ProductDetails) -> Unit)? = null
    ) {
        if (!billingReady) {
            if (reportErrors) onMessage("Google Play todavía no está listo. Probá nuevamente en unos segundos.")
            return
        }

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SUBSCRIPTION_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            val details = result.productDetailsList.firstOrNull { it.productId == SUBSCRIPTION_PRODUCT_ID }
            subscriptionProductDetails = details

            if (details != null) {
                activity.runOnUiThread { onReady?.invoke(details) }
                return@queryProductDetailsAsync
            }

            if (!reportErrors) return@queryProductDetailsAsync

            val unfetched = result.unfetchedProductList.firstOrNull { it.productId == SUBSCRIPTION_PRODUCT_ID }
            val reason = when (unfetched?.statusCode) {
                2 -> "El ID de la suscripción no tiene un formato válido."
                3 -> "Google Play todavía no encuentra help_monthly para esta instalación."
                4 -> "Google Play encuentra help_monthly, pero no hay un plan/oferta elegible para esta cuenta y región."
                else -> "Google Play no devolvió la suscripción help_monthly."
            }
            val googleDetail = billingResult.debugMessage.trim()
            val suffix = if (googleDetail.isNotBlank()) " · Google: $googleDetail" else " · código ${billingResult.responseCode}"
            activity.runOnUiThread { onMessage(reason + suffix) }
        }
    }

    fun launchSubscription(obfuscatedAccountId: String) {
        if (!billingReady) {
            onMessage("Google Play todavía no está listo. Probá nuevamente en unos segundos.")
            return
        }

        onMessage("Consultando la suscripción en Google Play…")
        querySubscriptionProduct(reportErrors = true) { details ->
            val offers = details.subscriptionOfferDetails.orEmpty()
            val offer = offers.firstOrNull { "trial30" in it.offerTags } ?: offers.firstOrNull()
            if (offer == null) {
                onMessage("Google Play devolvió help_monthly, pero no hay ningún plan disponible para comprar.")
                return@querySubscriptionProduct
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
                onMessage("Google Play no abrió la compra: ${result.responseCode} · ${result.debugMessage}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> onMessage("Compra cancelada.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> onMessage("Google Play no pudo completar la operación: ${billingResult.responseCode} · ${billingResult.debugMessage}")
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
