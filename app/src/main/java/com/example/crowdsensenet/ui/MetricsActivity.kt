package com.example.crowdsensenet.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.telephony.SignalStrength
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
                android.util.Log.d("MetricsActivity", "Updating metrics display...")
                
                // Check permissions first
                val hasPhonePermission = ContextCompat.checkSelfPermission(
                    this@MetricsActivity, 
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                
                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    this@MetricsActivity, 
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                android.util.Log.d("MetricsActivity", "Permissions - Phone: $hasPhonePermission, Location: $hasLocationPermission")
                
                // Get real-time network data
                val networkType = try {
                    if (hasPhonePermission) {
                        val type = com.example.crowdsensenet.utils.NetworkUtils.getNetworkType(this@MetricsActivity)
                        android.util.Log.d("MetricsActivity", "Network type: $type")
                        type
                    } else {
                        android.util.Log.d("MetricsActivity", "No phone permission")
                        "Permission Required"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetricsActivity", "Error getting network type", e)
                    "Error: ${e.message}"
                }
                
                val (cellId, pci) = try {
                    if (hasPhonePermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val cellInfo = com.example.crowdsensenet.utils.NetworkUtils.getCellInfo(this@MetricsActivity)
                        android.util.Log.d("MetricsActivity", "Cell info: $cellInfo")
                        cellInfo
                    } else {
                        android.util.Log.d("MetricsActivity", "Using fallback cell info")
                        Pair("API < 30", 0.0)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetricsActivity", "Error getting cell info", e)
                    Pair("Error: ${e.message}", 0.0)
                }
                
                // Get signal strength - use legacy method for all versions
                val (rsrp, rsrq) = try {
                    if (hasPhonePermission) {
                        val signal = com.example.crowdsensenet.utils.NetworkUtils.getSignalStrengthLegacy(this@MetricsActivity)
                        android.util.Log.d("MetricsActivity", "Signal strength: $signal")
                        signal
                    } else {
                        android.util.Log.d("MetricsActivity", "No phone permission for signal")
                        Pair(-85.0, -7.0) // Fallback values
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetricsActivity", "Error getting signal strength", e)
                    Pair(-85.0, -7.0) // Fallback values
                }
                
                // Get location
                val location = try {
                    if (hasLocationPermission) {
                        val loc = com.example.crowdsensenet.utils.LocationUtils.getCurrentLocation(this@MetricsActivity)
                        android.util.Log.d("MetricsActivity", "Location: $loc")
                        loc
                    } else {
                        android.util.Log.d("MetricsActivity", "No location permission")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetricsActivity", "Error getting location", e)
                    null
                }
                
                // Update UI with real data
                runOnUiThread {
                    rsrpValueText.text = "${rsrp.toInt()} dBm"
                    rsrqValueText.text = "${rsrq.toInt()} dB"
                    cellIdText.text = cellId
                    pciText.text = if (pci > 0) pci.toInt().toString() else "N/A"
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
                android.util.Log.e("MetricsActivity", "Error in updateMetricsDisplay", e)
                // Show fallback values on error
                runOnUiThread {
                    rsrpValueText.text = "Error"
                    rsrqValueText.text = "Error"
                    cellIdText.text = "Error"
                    pciText.text = "Error"
                    networkTechnologyText.text = "Error"
                    latitudeText.text = "Error"
                    longitudeText.text = "Error"
                }
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
