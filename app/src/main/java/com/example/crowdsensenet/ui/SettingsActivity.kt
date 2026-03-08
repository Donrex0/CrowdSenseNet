package com.example.crowdsensenet.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.crowdsensenet.R
import com.example.crowdsensenet.service.SensingService
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var samplingIntervalSpinner: Spinner
    private lateinit var backgroundLoggingSwitch: Switch
    private lateinit var testConnectionButton: Button
    private lateinit var testConnectionStatusText: TextView
    
    private lateinit var sharedPreferences: SharedPreferences
    // private lateinit var firebaseRepository: FirebaseRepository  // TEMPORARILY DISABLED
    
    companion object {
        private const val PREFS_NAME = "crowdsense_prefs"
        private const val KEY_SAMPLING_INTERVAL = "sampling_interval"
        private const val KEY_BACKGROUND_LOGGING = "background_logging"
        
        // Sampling intervals in milliseconds
        private val SAMPLING_INTERVALS = mapOf(
            "5 seconds" to 5000L,
            "10 seconds" to 10000L,
            "30 seconds" to 30000L,
            "60 seconds" to 60000L
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // firebaseRepository = FirebaseRepository()  // TEMPORARILY DISABLED
        
        initializeViews()
        setupNavigation()
        setupSamplingIntervalSpinner()
        setupBackgroundLoggingSwitch()
        setupConnectionTest()
        loadSettings()
    }
    
    private fun initializeViews() {
        samplingIntervalSpinner = findViewById(R.id.sampling_interval_dropdown)
        backgroundLoggingSwitch = findViewById(R.id.background_logging)
        testConnectionButton = findViewById(R.id.btn_test_connection_with_firebase)
        testConnectionStatusText = findViewById(R.id.test_connection_status)
    }
    
    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.navigation_settings
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
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
                R.id.navigation_settings -> true
                else -> false
            }
        }
        
        findViewById<View>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupSamplingIntervalSpinner() {
        val intervals = SAMPLING_INTERVALS.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        samplingIntervalSpinner.adapter = adapter
        
        samplingIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedInterval = intervals[position]
                val intervalMs = SAMPLING_INTERVALS[selectedInterval] ?: 5000L
                
                // Save preference
                sharedPreferences.edit()
                    .putLong(KEY_SAMPLING_INTERVAL, intervalMs)
                    .apply()
                
                // Update sensing service if running
                updateSensingServiceInterval(intervalMs)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupBackgroundLoggingSwitch() {
        backgroundLoggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            sharedPreferences.edit()
                .putBoolean(KEY_BACKGROUND_LOGGING, isChecked)
                .apply()
            
            if (isChecked) {
                // Start sensing if not already running
                startSensingIfNotRunning()
            } else {
                // Stop sensing service
                stopSensingService()
            }
        }
    }
    
    private fun setupConnectionTest() {
        testConnectionButton.setOnClickListener {
            testFirebaseConnection()
        }
    }
    
    private fun loadSettings() {
        // Load sampling interval
        val savedInterval = sharedPreferences.getLong(KEY_SAMPLING_INTERVAL, 5000L)
        val intervalEntry = SAMPLING_INTERVALS.entries.find { it.value == savedInterval }
        val intervalName = intervalEntry?.key ?: "5 seconds"
        val intervalIndex = SAMPLING_INTERVALS.keys.toList().indexOf(intervalName)
        if (intervalIndex >= 0) {
            samplingIntervalSpinner.setSelection(intervalIndex)
        }
        
        // Load background logging setting
        val backgroundLogging = sharedPreferences.getBoolean(KEY_BACKGROUND_LOGGING, false)
        backgroundLoggingSwitch.isChecked = backgroundLogging
    }
    
    private fun updateSensingServiceInterval(intervalMs: Long) {
        val intent = Intent(this, SensingService::class.java).apply {
            action = SensingService.ACTION_START
            putExtra(SensingService.EXTRA_SAMPLING_INTERVAL, intervalMs)
        }
        startService(intent)
    }
    
    private fun startSensingIfNotRunning() {
        val intent = Intent(this, SensingService::class.java).apply {
            action = SensingService.ACTION_START
            val intervalMs = sharedPreferences.getLong(KEY_SAMPLING_INTERVAL, 5000L)
            putExtra(SensingService.EXTRA_SAMPLING_INTERVAL, intervalMs)
        }
        startService(intent)
    }
    
    private fun stopSensingService() {
        val intent = Intent(this, SensingService::class.java).apply {
            action = SensingService.ACTION_STOP
        }
        startService(intent)
    }
    
    private fun testFirebaseConnection() {
        testConnectionButton.isEnabled = false
        testConnectionStatusText.text = "Testing..."
        testConnectionStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        
        lifecycleScope.launch {
            try {
                // Simulate connection test
                delay(2000)
                
                runOnUiThread {
                    testConnectionStatusText.text = "Demo Mode"
                    testConnectionStatusText.setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.holo_blue_dark))
                    testConnectionButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    testConnectionStatusText.text = "Demo Mode"
                    testConnectionStatusText.setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.holo_blue_dark))
                    testConnectionButton.isEnabled = true
                }
            }
        }
    }
}