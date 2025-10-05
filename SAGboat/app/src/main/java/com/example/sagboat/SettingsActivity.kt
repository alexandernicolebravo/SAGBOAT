package com.example.sagboat

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiStatusTextView: TextView
    private lateinit var ssidTextView: TextView
    private lateinit var passwordTextView: TextView
    private val esp32Ssid = "SAGBOAT"
    private val esp32Password = "12345678"

    private var hasShownAlert = false // Flag to prevent duplicate alerts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiStatusTextView = findViewById(R.id.wifiStatus)
        ssidTextView = findViewById(R.id.ssidText)
        passwordTextView = findViewById(R.id.passwordText)

        ssidTextView.text = getString(R.string.ssid_label, esp32Ssid)
        passwordTextView.text = getString(R.string.password_label, esp32Password)

        checkWifiConnectionStatus()

        findViewById<View>(R.id.checkWifiStatusButton).setOnClickListener {
            checkWifiConnectionStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        checkWifiConnectionStatus()
    }

    private fun checkWifiConnectionStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val currentSsid = wifiManager.connectionInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
            if (currentSsid == esp32Ssid) {
                wifiStatusTextView.text = getString(R.string.wifi_status_connected)
                Toast.makeText(this, "Connected to SAGBOAT Wi-Fi", Toast.LENGTH_SHORT).show()
                hasShownAlert = false // Reset alert flag on successful connection
            } else {
                wifiStatusTextView.text = getString(R.string.wifi_status_not_connected)
                if (!hasShownAlert) {
                    Toast.makeText(this, "Not connected to SAGBOAT Wi-Fi", Toast.LENGTH_SHORT).show()
                    hasShownAlert = true // Set flag to true after showing the alert
                }
            }
        } else {
            wifiStatusTextView.text = getString(R.string.wifi_status_not_connected)
            if (!hasShownAlert) {
                Toast.makeText(this, "Wi-Fi is not connected. Please connect to a Wi-Fi network.", Toast.LENGTH_SHORT).show()
                hasShownAlert = true // Set flag to true after showing the alert
            }
        }
    }

    fun openDeviceWifiSettings(view: View) {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
