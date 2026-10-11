package com.fabmue.tempomat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TransportType { BLE, TCP, DEMO }

data class LinkSettings(
    val transport: TransportType = TransportType.BLE,
    val bleAddress: String? = null,
    val bleName: String? = null,
    val tcpHost: String = DEFAULT_HOST,
    val tcpPort: Int = DEFAULT_PORT,
) {
    companion object {
        /** ESP32 SoftAP default gateway address. */
        const val DEFAULT_HOST = "192.168.4.1"
        const val DEFAULT_PORT = 3333
    }
}

private val Context.dataStore by preferencesDataStore(name = "link_settings")

class LinkSettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<LinkSettings> = store.data.map { p ->
        LinkSettings(
            transport = p[TRANSPORT]?.let { v -> TransportType.entries.firstOrNull { it.name == v } }
                ?: TransportType.BLE,
            bleAddress = p[BLE_ADDRESS],
            bleName = p[BLE_NAME],
            tcpHost = p[TCP_HOST] ?: LinkSettings.DEFAULT_HOST,
            tcpPort = p[TCP_PORT] ?: LinkSettings.DEFAULT_PORT,
        )
    }

    suspend fun setTransport(type: TransportType) = store.edit { it[TRANSPORT] = type.name }

    suspend fun setBleDevice(address: String, name: String?) = store.edit {
        it[BLE_ADDRESS] = address
        if (name != null) it[BLE_NAME] = name else it.remove(BLE_NAME)
    }

    suspend fun setTcp(host: String, port: Int) = store.edit {
        it[TCP_HOST] = host
        it[TCP_PORT] = port
    }

    private companion object {
        val TRANSPORT = stringPreferencesKey("transport")
        val BLE_ADDRESS = stringPreferencesKey("ble_address")
        val BLE_NAME = stringPreferencesKey("ble_name")
        val TCP_HOST = stringPreferencesKey("tcp_host")
        val TCP_PORT = intPreferencesKey("tcp_port")
    }
}
