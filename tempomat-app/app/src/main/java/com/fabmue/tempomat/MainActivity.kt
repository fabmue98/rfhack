package com.fabmue.tempomat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.fabmue.tempomat.ui.CruiseViewModel
import com.fabmue.tempomat.ui.DashboardScreen
import com.fabmue.tempomat.ui.SettingsScreen
import com.fabmue.tempomat.ui.theme.TempomatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CruiseViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onBluetoothPermission(hasBluetoothPermissions())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The radio is the dashboard: keep it lit while the app is in front.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.onBluetoothPermission(hasBluetoothPermissions())
        if (!hasBluetoothPermissions()) permissionLauncher.launch(bluetoothPermissions())

        setContent {
            TempomatTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showSettings = false },
                        onRequestBluetooth = { permissionLauncher.launch(bluetoothPermissions()) },
                        hasBluetoothPermission = ::hasBluetoothPermissions,
                    )
                } else {
                    DashboardScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    private fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBluetoothPermissions(): Boolean = bluetoothPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
