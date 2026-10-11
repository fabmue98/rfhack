package com.fabmue.checkoutmate

import android.app.Application
import android.content.Context
import com.fabmue.checkoutmate.billing.BillingManager
import com.fabmue.checkoutmate.data.CheckoutRepository
import com.fabmue.checkoutmate.data.EntitlementRepository
import com.fabmue.checkoutmate.data.HistoryRepository
import com.fabmue.checkoutmate.data.PreferencesRepository
import com.fabmue.checkoutmate.data.local.AppDatabase
import com.fabmue.checkoutmate.domain.X01Config
import com.fabmue.checkoutmate.domain.X01Match
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny hand-rolled DI container — no framework needed for an app this size. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = PreferencesRepository(context)
    val entitlements = EntitlementRepository(prefs, appScope)
    val billing = BillingManager(context, entitlements)
    val checkout = CheckoutRepository(context)
    private val db = AppDatabase.get(context)
    val history = HistoryRepository(db.dao())

    // Hand-off for the match screen (navigation can't carry complex objects).
    @Volatile var pendingNewConfig: X01Config? = null
    @Volatile var pendingResume: X01Match? = null

    fun start() {
        billing.start()
    }
}

class CheckoutMateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
