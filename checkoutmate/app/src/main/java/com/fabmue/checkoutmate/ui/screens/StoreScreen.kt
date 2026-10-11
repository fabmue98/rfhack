package com.fabmue.checkoutmate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.billing.BillingStatus
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.data.Products
import com.fabmue.checkoutmate.ui.findActivity

private data class PackMeta(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val feature: Feature,
    val fallbackPrice: String,
    val highlight: Boolean = false,
)

private val PACKS = listOf(
    PackMeta(Products.CHECKOUT, R.string.store_pack_checkout_title, R.string.store_pack_checkout_desc, Feature.CHECKOUT, "€2.99"),
    PackMeta(Products.TRAINING, R.string.store_pack_training_title, R.string.store_pack_training_desc, Feature.TRAINING, "€2.99"),
    PackMeta(Products.STATS, R.string.store_pack_stats_title, R.string.store_pack_stats_desc, Feature.STATS, "€2.99"),
    PackMeta(Products.PRO, R.string.store_pro_title, R.string.store_pro_desc, Feature.VOICE, "€6.99", highlight = true),
)

@Composable
fun StoreScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val prices by container.billing.prices.collectAsState()
    val status by container.billing.status.collectAsState()
    val owned by container.entitlements.ownedProducts.collectAsState()
    val entitlements by container.entitlements.entitlements.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(stringResource(R.string.store_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.store_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (status == BillingStatus.UNAVAILABLE) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(stringResource(R.string.store_unavailable), Modifier.padding(16.dp))
                    }
                }

                PACKS.forEach { pack ->
                    val isOwned = when (pack.id) {
                        Products.PRO -> owned.contains(Products.PRO)
                        else -> entitlements.contains(pack.feature)
                    }
                    PackCard(
                        title = stringResource(pack.titleRes),
                        desc = stringResource(pack.descRes),
                        price = prices[pack.id]?.takeIf { it.isNotBlank() } ?: pack.fallbackPrice,
                        owned = isOwned,
                        highlight = pack.highlight,
                        onBuy = {
                            val activity = context.findActivity()
                            val ok = activity != null && container.billing.launchPurchase(activity, pack.id)
                            if (!ok) Toast.makeText(context, R.string.store_unavailable, Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                OutlinedButton(
                    onClick = {
                        container.billing.queryPurchases()
                        Toast.makeText(context, R.string.store_restored, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.store_restore)) }
            }
        }
    }
}

@Composable
private fun PackCard(
    title: String,
    desc: String,
    price: String,
    owned: Boolean,
    highlight: Boolean,
    onBuy: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (highlight) {
                    Text(stringResource(R.string.store_best_value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (owned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("  " + stringResource(R.string.common_owned), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.store_buy) + "  ·  " + price)
                }
            }
        }
    }
}
