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
    private var isSensing = false // Track sensing state
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure OpenStreetMap
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
        
        setContentView(R.layout.activity_metrics)
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
        if (bottomNav == null) {
            android.util.Log.e("MetricsActivity", "Bottom navigation not found!")
            return
        }
        
        android.util.Log.d("MetricsActivity", "Bottom navigation found, setting up listener")
        bottomNav.selectedItemId = R.id.navigation_metrics
        
        bottomNav.setOnItemSelectedListener { item ->
            android.util.Log.d("MetricsActivity", "Navigation item selected: ${item.itemId}")
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    android.util.Log.d("MetricsActivity", "Navigating to Dashboard")
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_metrics -> {
                    android.util.Log.d("MetricsActivity", "Already on Metrics")
                    true
                }
                R.id.navigation_uploads -> {
                    android.util.Log.d("MetricsActivity", "Navigating to Uploads")
                    startActivity(Intent(this, UploadsActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_settings -> {
                    android.util.Log.d("MetricsActivity", "Navigating to Settings")
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> {
                    android.util.Log.e("MetricsActivity", "Unknown navigation item: ${item.itemId}")
                    false
                }
            }
        }
    }
    
    private fun setupMap() {
        map = findViewById(R.id.long_lat_map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        // Set default zoom and center on G-block, University of Buea
        val mapController = map.controller
        mapController.setZoom(16.0) // Higher zoom for G-block detail
        
        // Center on G-block, University of Buea initially
        val gBlockLocation = GeoPoint(4.1518, 9.2425)
        mapController.setCenter(gBlockLocation)
        
        // Add initial marker for G-block
        val gBlockMarker = Marker(map)
        gBlockMarker.position = gBlockLocation
        gBlockMarker.title = "G-Block, University of Buea"
        gBlockMarker.subDescription = "4.1518°N, 9.2425°E"
        gBlockMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(gBlockMarker)
        
        // Update with current location
        updateMapLocation()
    }
    
    private fun updateMapLocation() {
        lifecycleScope.launch {
            try {
                val location = LocationUtils.getCurrentLocation(this@MetricsActivity)
                location?.let {
                    updateMapWithLocation(it.latitude, it.longitude)
                }
            } catch (e: Exception) {
                android.util.Log.e("MetricsActivity", "Error updating map location", e)
            }
        }
    }
    
    private fun updateMapWithLocation(latitude: Double, longitude: Double) {
        try {
            val currentLocation = GeoPoint(latitude, longitude)
            
            // Clear existing markers except the G-block marker
            val gBlockMarker = map.overlays.find { it is Marker && it.title == "G-Block, University of Buea" }
            map.overlays.clear()
            
            // Re-add G-block marker if it existed
            gBlockMarker?.let { map.overlays.add(it) }
            
            // Add current location marker
            val currentMarker = Marker(map)
            currentMarker.position = currentLocation
            currentMarker.title = "Current Location"
            currentMarker.subDescription = "G-Block Area: %.6f°N, %.6f°E".format(latitude, longitude)
            currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(currentMarker)
            
            // Center map on current location
            map.controller.setCenter(currentLocation)
            
            android.util.Log.d("MetricsActivity", "Map updated with location: $latitude, $longitude")
        } catch (e: Exception) {
            android.util.Log.e("MetricsActivity", "Error updating map with location", e)
        }
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
                    if (isSensing) "Unknown" else NetworkUtils.getLastKnownNetworkType()
                }
                
                val signal = try {
                    if (hasPhonePermission) {
                        if (isSensing) {
                            // Get real-time signal when sensing is on
                            val sig = com.example.crowdsensenet.utils.NetworkUtils.getSignalStrengthLegacy(this@MetricsActivity)
                            // Store last known values
                            NetworkUtils.setLastKnownSignalStrength(sig.first, sig.second)
                            sig
                        } else {
                            // Use last known values when sensing is off
                            NetworkUtils.getLastKnownSignalStrength()
                        }
                    } else {
                        android.util.Log.d("MetricsActivity", "No phone permission for signal")
                        if (isSensing) Pair(-85.0, -7.0) else NetworkUtils.getLastKnownSignalStrength()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetricsActivity", "Error getting signal strength", e)
                    if (isSensing) Pair(-85.0, -7.0) else NetworkUtils.getLastKnownSignalStrength()
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
