package com.example.sagboat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private val esp32HotspotIp = "192.168.4.1"
    private val esp32CamIp = "192.168.4.13"
    private val esp32Ssid = "SAGBOAT"
    private lateinit var dbHelper: DBHelper // Local database helper
    private lateinit var webView: WebView
    private lateinit var notConnectedText: TextView
    private lateinit var cameraContainer: View
    private lateinit var startStopStreamButton: Button
    private lateinit var captureButton: Button
    private lateinit var saveButton: Button
    private lateinit var phText: TextView
    private lateinit var phLevelText: TextView
    private lateinit var turbidityText: TextView
    private lateinit var turbidityLevelText: TextView
    private lateinit var infraredText: TextView
    private var isStreaming = false

    private val permissionsRequestCode = 1
    private val requiredPermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE
            )
        }
        else -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components and database helper
        initializeComponents()
        dbHelper = DBHelper(this) // Initialize local database helper

        // Check permissions
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!hasPermissions(*requiredPermissions)) {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionsRequestCode)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeComponents() {
        webView = findViewById(R.id.webView)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        notConnectedText = findViewById(R.id.notConnectedText)
        cameraContainer = findViewById(R.id.cameraContainer)
        startStopStreamButton = findViewById(R.id.startStopStreamButton)
        captureButton = findViewById(R.id.captureButton)
        saveButton = findViewById(R.id.saveButton)
        phText = findViewById(R.id.phText)
        phLevelText = findViewById(R.id.phLevelText)
        turbidityText = findViewById(R.id.turbidityText)
        turbidityLevelText = findViewById(R.id.turbidityLevelText)
        infraredText = findViewById(R.id.infraredText)

        findViewById<View>(R.id.settingsIcon).setOnClickListener {
            openSettings()
        }

        findViewById<View>(R.id.activityLogIcon).setOnClickListener {
            openLog()
        }

        webView.settings.apply {
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                Log.e(tag, "Error loading URL: ${error.description}")
                runOnUiThread {
                    webView.visibility = View.GONE
                    notConnectedText.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d(tag, "Page finished loading: $url")
            }
        }

        startStopStreamButton.setOnClickListener {
            if (isConnectedToSAGBOAT()) {
                toggleStream()
            } else {
                showAlert(getString(R.string.connect_to_sagboat))
            }
        }

        captureButton.setOnClickListener {
            if (isStreaming) {
                captureImage()
            } else {
                showAlert(getString(R.string.please_start_streaming))
            }
        }

        saveButton.setOnClickListener {
            if (isConnectedToSAGBOAT() && isStreaming) {
                saveData()
            } else {
                showAlert(getString(R.string.connect_to_sagboat_and_start_streaming))
            }
        }

        clearSensorData()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openLog() {
        Log.d(tag, "Opening LogActivity")
        try {
            val intent = Intent(this, LogActivity::class.java)
            startActivity(intent)
            Log.d(tag, "LogActivity started")
        } catch (e: Exception) {
            Log.e(tag, "Failed to open LogActivity: ${e.message}")
            showAlert(getString(R.string.failed_open_log_activity))
        }
    }

    private fun toggleStream() {
        if (isStreaming) {
            stopStream()
        } else {
            startStream()
        }
    }

    private fun startStream() {
        val url = "http://$esp32CamIp/stream"
        webView.visibility = View.VISIBLE
        notConnectedText.visibility = View.GONE
        webView.loadUrl(url)
        isStreaming = true
        startStopStreamButton.setText(R.string.stop_stream)
        startStopStreamButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0)
        startDataCollection()
        fetchSensorData()
        checkInfraredSensor()
    }

    private fun stopStream(error: Boolean = false) {
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
        notConnectedText.visibility = View.VISIBLE
        isStreaming = false
        startStopStreamButton.setText(R.string.start_stream)
        startStopStreamButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_start, 0, 0, 0)
        stopDataCollection()
        clearSensorData(error)
    }

    private fun captureImage() {
        if (!isStreaming) {
            showAlert(getString(R.string.please_start_streaming))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$esp32CamIp/capture")
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                saveImageToGallery(bitmap)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.image_captured_saved), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error capturing image: ${e.message}")
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val uri = saveImageToGalleryAndroid13(bitmap)
            uri?.let {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.failed_save_image), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SAGBOAT_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SAGBOAT")
                put(MediaStore.Images.Media.IS_PENDING, true)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                resolver.update(uri, values, null, null)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToGalleryAndroid13(bitmap: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SAGBOAT_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SAGBOAT")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        return try {
            uri?.let {
                val outputStream = resolver.openOutputStream(it) ?: throw IOException("Failed to get output stream.")
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) {
                    throw IOException("Failed to save bitmap.")
                }
                outputStream.close()
                it
            }
        } catch (e: IOException) {
            Log.e(tag, "Error writing to file: ${e.message}")
            uri?.let { resolver.delete(it, null, null) }
            null
        }
    }

    private fun saveData() {
        if (!isStreaming) {
            showAlert(getString(R.string.stream_not_started))
            return
        }
        val ph = phText.text.toString().substringAfter(": ").toDoubleOrNull() ?: 0.0
        val phLevel = phLevelText.text.toString().substringAfter(": ").ifEmpty { "N/A" }
        val turbidity = turbidityText.text.toString().substringAfter(": ").substringBefore(" NTU").toDoubleOrNull() ?: 0.0
        val turbidityLevel = turbidityLevelText.text.toString().substringAfter(": ").ifEmpty { "N/A" }

        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val (date, time) = currentDateTime.split(" ")

        // Create a log entry and save it locally in the database
        val logEntry = LogEntry(
            date = date,
            time = time,
            ph = ph,
            phLevel = phLevel,
            turbidity = turbidity,
            turbidityLevel = turbidityLevel
        )
        dbHelper.addLogEntry(logEntry) // Save to local database
        Toast.makeText(this, getString(R.string.data_saved), Toast.LENGTH_SHORT).show()
    }

    private fun fetchSensorData() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isStreaming) {
                val sensorData = fetchSensorDataFromEsp32()
                val ph = sensorData.first
                val turbidity = sensorData.second
                val phLevel = getPhLevel(ph) ?: "N/A"
                val turbidityLevel = getTurbidityLevel(turbidity) ?: "N/A"
                withContext(Dispatchers.Main) {
                    phText.text = if (ph != null) getString(R.string.ph_text, ph) else getString(R.string.ph_null_text)
                    phLevelText.text = getString(R.string.ph_level_description, phLevel)
                    turbidityText.text = if (turbidity != null) getString(R.string.turbidity_text, turbidity) else getString(R.string.turbidity_null_text)
                    turbidityLevelText.text = getString(R.string.turbidity_level_description, turbidityLevel)
                }
                delay(5000) // Fetch data every 5 seconds
            }
        }
    }

    private fun clearSensorData(error: Boolean = false) {
        runOnUiThread {
            if (error) {
                phText.setText(R.string.ph_inactive)
                phLevelText.setText(R.string.ph_level_inactive)
                turbidityText.setText(R.string.turbidity_inactive)
                turbidityLevelText.setText(R.string.turbidity_level_inactive)
                infraredText.setText(R.string.infrared_inactive)
            } else {
                phText.text = getString(R.string.ph_null_text)
                phLevelText.text = getString(R.string.ph_level_description, "N/A")
                turbidityText.text = getString(R.string.turbidity_null_text)
                turbidityLevelText.text = getString(R.string.turbidity_level_description, "N/A")
                infraredText.text = getString(R.string.infrared_null_text)
            }
        }
    }

    private fun fetchSensorDataFromEsp32(): Pair<Float?, Float?> {
        return try {
            val url = URL("http://$esp32HotspotIp/data")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "Failed to fetch sensor data: HTTP ${connection.responseCode}")
                return Pair(null, null)
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val ph = if (json.has("pH")) json.optDouble("pH", Double.NaN).toFloat() else null
            val turbidity = if (json.has("turbidity")) json.optDouble("turbidity", Double.NaN).toFloat() else null

            if (ph == null || ph.isNaN()) Log.e(tag, "pH value missing or invalid in JSON")
            if (turbidity == null || turbidity.isNaN()) Log.e(tag, "Turbidity value missing or invalid in JSON")

            Pair(
                if (ph != null && !ph.isNaN()) ph else null,
                if (turbidity != null && !turbidity.isNaN()) turbidity else null
            )
        } catch (e: Exception) {
            Log.e(tag, "Error fetching sensor data: ${e.message}")
            Pair(null, null)
        }
    }

    private fun checkInfraredSensor() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isStreaming) {
                if (isConnectedToSAGBOAT()) {
                    val irStatus = fetchInfraredSensorStatus()
                    val infraredStatus = when (irStatus) {
                        true -> getString(R.string.infrared_collecting)
                        false -> getString(R.string.infrared_full)
                        null -> getString(R.string.infrared_null_text)
                    }
                    withContext(Dispatchers.Main) {
                        infraredText.text = infraredStatus
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        infraredText.text = getString(R.string.infrared_null_text)
                    }
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private fun fetchInfraredSensorStatus(): Boolean? {
        return try {
            val url = URL("http://$esp32HotspotIp/data")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "Failed to fetch IR sensor status: HTTP ${connection.responseCode}")
                return null
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (!json.has("ir")) {
                Log.e(tag, "IR value missing in JSON")
                return null
            }
            json.optBoolean("ir", false)
        } catch (e: Exception) {
            Log.e(tag, "Error fetching IR sensor status: ${e.message}")
            null
        }
    }

    private fun startDataCollection() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$esp32HotspotIp/start")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == 200) {
                    Log.d(tag, "Data collection started")
                } else {
                    Log.e(tag, "Failed to start data collection")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error starting data collection: ${e.message}")
            }
        }
    }

    private fun stopDataCollection() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$esp32HotspotIp/stop")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == 200) {
                    Log.d(tag, "Data collection stopped")
                } else {
                    Log.e(tag, "Failed to stop data collection")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error stopping data collection: ${e.message}")
            }
        }
    }

    private fun getPhLevel(ph: Float?): String? {
        return ph?.let {
            when {
                ph < 3 -> getString(R.string.ph_level_acidic)
                ph < 5 -> getString(R.string.ph_level_acidic)
                ph < 7 -> getString(R.string.ph_level_neutral)
                ph < 9 -> getString(R.string.ph_level_alkaline)
                else -> getString(R.string.ph_level_alkaline)
            }
        }
    }

    private fun getTurbidityLevel(turbidity: Float?): String? {
        return turbidity?.let {
            when {
                turbidity < 1 -> getString(R.string.turbidity_level_clear)
                turbidity < 5 -> getString(R.string.turbidity_level_fairly)
                else -> getString(R.string.turbidity_level_dirty)
            }
        }
    }

    private fun isConnectedToSAGBOAT(): Boolean {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ssid = wifiManager.connectionInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
        Log.d(tag, "Current SSID: $ssid")
        return ssid != null && ssid == esp32Ssid
    }

    private fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeComponents()
            } else {
                Toast.makeText(this, "Permissions denied, some features may not work", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}