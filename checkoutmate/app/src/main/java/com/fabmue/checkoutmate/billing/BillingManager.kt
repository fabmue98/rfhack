package com.fabmue.checkoutmate.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.fabmue.checkoutmate.data.EntitlementRepository
import com.fabmue.checkoutmate.data.Products
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BillingStatus { CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * Wraps Google Play Billing (library 9.x). Fully defensive: if the Play Store
 * is absent (e.g. a side-loaded debug build) nothing crashes — prices stay
 * empty and the store screen shows an "unavailable" note.
 */
class BillingManager(
    context: Context,
    private val entitlements: EntitlementRepository,
) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(BillingStatus.CONNECTING)
    val status: StateFlow<BillingStatus> = _status

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices

    private val ownedSet = linkedSetOf<String>()
    private var productDetails: Map<String, ProductDetails> = emptyMap()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                if (purchases != null) handlePurchases(purchases)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> Unit
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        _status.value = BillingStatus.CONNECTING
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = BillingStatus.CONNECTED
                    queryProducts()
                    queryPurchases()
                } else {
                    _status.value = BillingStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnection is enabled on the client; just reflect state.
                _status.value = BillingStatus.CONNECTING
            }
        })
    }

    /** Re-check ownership, e.g. from Activity.onResume. */
    fun refresh() {
        if (billingClient.isReady) {
            queryProducts()
            queryPurchases()
        } else {
            start()
        }
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val pd = productDetails[productId] ?: return false
        val offerToken = pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
        if (offerToken != null) paramsBuilder.setOfferToken(offerToken)
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun queryProducts() {
        if (!billingClient.isReady) return
        val products = Products.ALL.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = queryResult.productDetailsList
                productDetails = list.associateBy { it.productId }
                _prices.value = productDetails.mapValues { (_, pd) ->
                    pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice ?: ""
                }
            }
        }
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        var changed = false
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (ownedSet.addAll(purchase.products)) changed = true
                if (!purchase.isAcknowledged) acknowledge(purchase)
            }
        }
        if (changed || ownedSet.isNotEmpty()) {
            entitlements.setBillingOwned(ownedSet.toSet())
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { _ -> /* best effort */ }
    }
}
