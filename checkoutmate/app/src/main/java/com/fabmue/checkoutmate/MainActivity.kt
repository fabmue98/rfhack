package com.fabmue.checkoutmate

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fabmue.checkoutmate.data.PreferencesRepository
import com.fabmue.checkoutmate.ui.LocaleHelper
import com.fabmue.checkoutmate.ui.navigation.AppRoot

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = PreferencesRepository(newBase).languageBlocking()
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CheckoutMateApp).container
        setContent {
            AppRoot(container = container, onRecreate = { recreate() })
        }
    }

    override fun onResume() {
        super.onResume()
        (application as CheckoutMateApp).container.billing.refresh()
    }
}
