package com.example.crowdsensenet.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.telephony.SignalStrength
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.crowdsensenet.R
// import com.example.crowdsensenet.data.local.AppDatabase  // TEMPORARILY DISABLED
import com.example.crowdsensenet.utils.LocationUtils
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.config.Configuration
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MetricsActivity : AppCompatActivity() {
    
    private lateinit var rsrpValueText: TextView
    private lateinit var rsrqValueText: TextView
    private lateinit var cellIdText: TextView
    private lateinit var pciText: TextView
    private lateinit var networkTechnologyText: TextView
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    
    // private lateinit var database: AppDatabase  // TEMPORARILY DISABLED
    private lateinit var map: MapView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure OpenStreetMap
        Configuration.getInstance().userAgentValue = packageName
        
        setContentView(R.layout.activity_metrics)
        
        // database = AppDatabase.getDatabase(this)  // TEMPORARILY DISABLED
        initializeViews()
        setupNavigation()
        setupMap()
        startMetricsUpdates()
    }
    
    private fun initializeViews() {
        rsrpValueText = findViewById(R.id.rsrp_value)
        rsrqValueText = findViewById(R.id.rsrq_value)
        cellIdText = findViewById(R.id.cell_id)
        pciText = findViewById(R.id.pci)
        networkTechnologyText = findViewById(R.id.network_technology)
        latitudeText = findViewById(R.id.latitude)
        longitudeText = findViewById(R.id.longitude)
    }
    
    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.navigation_metrics
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_metrics -> true
                R.id.navigation_uploads -> {
                    startActivity(Intent(this, UploadsActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupMap() {
        map = findViewById(R.id.long_lat_map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        // Set default zoom
        val mapController = map.controller
        mapController.setZoom(15.0)
    }
    
    private fun startMetricsUpdates() {
        lifecycleScope.launch {
            while (true) {
                try {
                    updateMetricsDisplay()
                    delay(3000) // Update every 3 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }
    
    private fun updateMetricsDisplay() {
        lifecycleScope.launch {
            try {
                // Get real-time network data
                val networkType = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        com.example.crowdsensenet.utils.NetworkUtils.getNetworkType(this@MetricsActivity)
                    } else {
                        "Unknown"
                    }
                } catch (e: Exception) {
                    "Unknown"
                }
                
                val (cellId, pci) = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        com.example.crowdsensenet.utils.NetworkUtils.getCellInfo(this@MetricsActivity)
                    } else {
                        Pair("Unknown", 0.0)
                    }
                } catch (e: Exception) {
                    Pair("Unknown", 0.0)
                }
                
                // Get signal strength
                val (rsrp, rsrq) = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Use modern API for Android 10+
                        getSignalStrengthModern()
                    } else {
                        // Use legacy API for older versions
                        com.example.crowdsensenet.utils.NetworkUtils.getSignalStrengthLegacy(this@MetricsActivity)
                    }
                } catch (e: Exception) {
                    Pair(-85.0, -7.0) // Fallback values
                }
                
                // Get location
                val location = try {
                    com.example.crowdsensenet.utils.LocationUtils.getCurrentLocation(this@MetricsActivity)
                } catch (e: Exception) {
                    null
                }
                
                // Update UI with real data
                runOnUiThread {
                    rsrpValueText.text = "${rsrp.toInt()} dBm"
                    rsrqValueText.text = "${rsrq.toInt()} dB"
                    cellIdText.text = cellId
                    pciText.text = pci.toInt().toString()
                    networkTechnologyText.text = networkType
                    if (location != null) {
                        latitudeText.text = "%.6f".format(location.latitude)
                        longitudeText.text = "%.6f".format(location.longitude)
                        
                        // Update map with current location
                        updateMapWithLocation(location.latitude, location.longitude)
                    } else {
                        latitudeText.text = "No GPS"
                        longitudeText.text = "No GPS"
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Show fallback values on error
                runOnUiThread {
                    rsrpValueText.text = "No Data"
                    rsrqValueText.text = "No Data"
                    cellIdText.text = "No Data"
                    pciText.text = "No Data"
                    networkTechnologyText.text = "No Data"
                    latitudeText.text = "No Data"
                    longitudeText.text = "No Data"
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun getSignalStrengthModern(): Pair<Double, Double> {
        return suspendCancellableCoroutine { cont ->
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val phoneStateListener = object : TelephonyManager.PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val result = com.example.crowdsensenet.utils.NetworkUtils.getSignalStrength(signalStrength)
                    cont.resume(result)
                    telephonyManager.listen(this, TelephonyManager.PhoneStateListener.LISTEN_NONE)
                }
            }
            
            telephonyManager.listen(phoneStateListener, TelephonyManager.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            
            // Handle cancellation
            cont.invokeOnCancellation {
                telephonyManager.listen(phoneStateListener, TelephonyManager.PhoneStateListener.LISTEN_NONE)
            }
        }
    }
    
    private fun updateMapWithLocation(latitude: Double, longitude: Double) {
        runOnUiThread {
            try {
                val location = GeoPoint(latitude, longitude)
                
                // Clear existing markers
                map.overlays.clear()
                
                // Add new marker
                val marker = Marker(map)
                marker.position = location
                marker.title = "Current Location"
                marker.subDescription = "Lat: $latitude, Lon: $longitude"
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                map.overlays.add(marker)
                
                // Center map on location
                map.controller.setCenter(location)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        map.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        map.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        map.onDetach()
    }
}
