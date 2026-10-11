package com.fabmue.checkoutmate.data

import com.fabmue.checkoutmate.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Unlockable features. */
enum class Feature { CHECKOUT, TRAINING, STATS, VOICE, THEMES }

/** One-time (non-consumable) product ids and what each grants. */
object Products {
    const val CHECKOUT = "unlock_checkout"
    const val TRAINING = "unlock_training"
    const val STATS = "unlock_stats"
    const val PRO = "unlock_pro"

    /** Individual packs shown as separate buys; PRO is the bundle. */
    val PACKS = listOf(CHECKOUT, TRAINING, STATS)
    val ALL = listOf(CHECKOUT, TRAINING, STATS, PRO)

    fun features(id: String): Set<Feature> = when (id) {
        CHECKOUT -> setOf(Feature.CHECKOUT)
        TRAINING -> setOf(Feature.TRAINING)
        STATS -> setOf(Feature.STATS)
        PRO -> setOf(Feature.CHECKOUT, Feature.TRAINING, Feature.STATS, Feature.VOICE, Feature.THEMES)
        else -> emptySet()
    }
}

/**
 * Single source of truth for what the user owns. Combines live Billing state
 * with a persisted cache (so unlocks survive offline) and a debug override.
 */
class EntitlementRepository(
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val billingOwned = MutableStateFlow<Set<String>>(emptySet())

    val ownedProducts: StateFlow<Set<String>> =
        combine(billingOwned, prefs.owned) { live, cached -> live + cached }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val entitlements: StateFlow<Set<Feature>> =
        combine(billingOwned, prefs.owned, prefs.devUnlock) { live, cached, dev ->
            val base = (live + cached).flatMap { Products.features(it) }.toMutableSet()
            if (dev && BuildConfig.DEBUG) base.addAll(Feature.entries)
            base
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun has(feature: Feature): Boolean = entitlements.value.contains(feature)

    /** Called by BillingManager when ownership changes; also caches for offline. */
    fun setBillingOwned(ids: Set<String>) {
        billingOwned.value = ids
        scope.launch {
            val merged = ids + prefs.owned.first()
            prefs.setOwned(merged)
        }
    }
}
