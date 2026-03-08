package com.example.crowdsensenet.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.crowdsensenet.R
// import com.example.crowdsensenet.data.local.AppDatabase  // TEMPORARILY DISABLED
import com.example.crowdsensenet.service.SensingService
import com.example.crowdsensenet.utils.LocationUtils
import com.example.crowdsensenet.utils.NetworkUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.config.Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var startSensingButton: LinearLayout
    private lateinit var startSensingButtonText: TextView
    private lateinit var sensingStateText: TextView
    private lateinit var networkStateText: TextView
    private lateinit var networkRatingText: TextView
    private lateinit var gpsStatusText: TextView
    private lateinit var gpsCoordinatesText: TextView
    
    private var isSensing = false
    // private lateinit var database: AppDatabase  // TEMPORARILY DISABLED
    private lateinit var map: MapView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure OpenStreetMap
        Configuration.getInstance().userAgentValue = packageName
        
        setContentView(R.layout.activity_dashboard)
        
        // database = AppDatabase.getDatabase(this)  // TEMPORARILY DISABLED
        initializeViews()
        setupNavigation()
        setupClickListeners()
        setupMap()
        startUIUpdates()
    }
    
    private fun initializeViews() {
        startSensingButton = findViewById(R.id.start_sensing_button)
        startSensingButtonText = startSensingButton.findViewById(R.id.start_sensing_button_text)
        sensingStateText = findViewById(R.id.sensing_state)
        networkStateText = findViewById(R.id.network_state)
        networkRatingText = findViewById(R.id.network_rating)
        gpsStatusText = findViewById(R.id.gps_status)
        gpsCoordinatesText = findViewById(R.id.gps_coordinates)
    }
    
    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.navigation_dashboard
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> true
                R.id.navigation_metrics -> {
                    startActivity(Intent(this, MetricsActivity::class.java))
                    finish()
                    true
                }
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
    
    private fun setupClickListeners() {
        startSensingButton.setOnClickListener {
            if (isSensing) {
                stopSensing()
            } else {
                startSensing()
            }
        }
    }
    
    private fun startSensing() {
        val intent = Intent(this, SensingService::class.java).apply {
            action = SensingService.ACTION_START
            putExtra(SensingService.EXTRA_SAMPLING_INTERVAL, 5000L) // 5 seconds
        }
        
        ContextCompat.startForegroundService(this, intent)
        
        isSensing = true
        startSensingButtonText.text = "Stop Sensing"
        sensingStateText.text = "ON"
        sensingStateText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }
    
    private fun stopSensing() {
        val intent = Intent(this, SensingService::class.java).apply {
            action = SensingService.ACTION_STOP
        }
        
        startService(intent)
        
        isSensing = false
        startSensingButtonText.text = "Start Sensing"
        sensingStateText.text = "OFF"
        sensingStateText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }
    
    private fun startUIUpdates() {
        lifecycleScope.launch {
            while (true) {
                try {
                    updateNetworkInfo()
                    updateLocationInfo()
                    delay(2000) // Update every 2 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }
    
    private fun updateNetworkInfo() {
        try {
            // Check if we have phone state permission
            val hasPhonePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // Permissions granted at install time for older versions
            }
            
            val networkType = if (hasPhonePermission) {
                NetworkUtils.getNetworkType(this)
            } else {
                "Permission Required"
            }
            networkStateText.text = networkType
            
            // Get real signal strength like MetricsActivity
            if (hasPhonePermission) {
                try {
                    val (rsrp, rsrq) = NetworkUtils.getSignalStrengthLegacy(this)
                    networkRatingText.text = "${rsrp.toInt()} dBm"
                    
                    // Color code based on signal strength
                    val color = when {
                        rsrp > -90 -> android.R.color.holo_green_dark
                        rsrp in -110.0..-90.0 -> android.R.color.holo_orange_dark
                        else -> android.R.color.holo_red_dark
                    }
                    networkRatingText.setTextColor(ContextCompat.getColor(this, color))
                    
                    android.util.Log.d("DashboardActivity", "Signal strength: ($rsrp, $rsrq)")
                } catch (e: Exception) {
                    networkRatingText.text = "Error"
                    networkRatingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    android.util.Log.e("DashboardActivity", "Error getting signal strength", e)
                }
            } else {
                networkRatingText.text = "Permission Required"
                networkRatingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
        } catch (e: Exception) {
            networkStateText.text = "Unknown"
            networkRatingText.text = "Unknown"
            android.util.Log.e("DashboardActivity", "Error in updateNetworkInfo", e)
        }
    }
    
    private fun updateLocationInfo() {
        try {
            val hasPermission = LocationUtils.hasLocationPermission(this)
            gpsStatusText.text = if (hasPermission) "Active" else "Inactive"
            gpsStatusText.setTextColor(
                if (hasPermission) 
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                else 
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            
            if (hasPermission) {
                lifecycleScope.launch {
                    try {
                        val location = LocationUtils.getCurrentLocation(this@DashboardActivity)
                        location?.let {
                            val formatted = LocationUtils.formatCoordinates(it.latitude, it.longitude)
                            gpsCoordinatesText.text = formatted
                        } ?: run {
                            gpsCoordinatesText.text = "No location data"
                        }
                    } catch (e: Exception) {
                        gpsCoordinatesText.text = "Location error"
                    }
                }
            } else {
                gpsCoordinatesText.text = "Permission denied"
            }
        } catch (e: Exception) {
            gpsStatusText.text = "Error"
            gpsCoordinatesText.text = "Location unavailable"
        }
    }
    
    private fun setupMap() {
        map = findViewById(R.id.map_view)
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
        
        // Show current location on map
        updateMapLocation()
    }
    
    private fun updateMapLocation() {
        lifecycleScope.launch {
            try {
                val location = LocationUtils.getCurrentLocation(this@DashboardActivity)
                location?.let {
                    val currentLocation = GeoPoint(it.latitude, it.longitude)
                    
                    // Clear existing markers
                    map.overlays.clear()
                    
                    // Add new marker
                    val marker = Marker(map)
                    marker.position = currentLocation
                    marker.title = "Current Location"
                    marker.subDescription = "Lat: ${it.latitude}, Lon: ${it.longitude}"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                    
                    // Center map on location
                    map.controller.setCenter(currentLocation)
                }
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
        if (isSensing) {
            stopSensing()
        }
        map.onDetach()
    }
}