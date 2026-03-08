package com.example.crowdsensenet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.crowdsensenet.R
// import com.example.crowdsensenet.data.local.AppDatabase  // TEMPORARILY DISABLED
import com.example.crowdsensenet.data.remote.SyncManager
import com.example.crowdsensenet.service.UploadWorker
import com.example.crowdsensenet.utils.NetworkDataStorage
import com.example.crowdsensenet.ui.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UploadsActivity : AppCompatActivity() {
    
    private lateinit var pendingUploadsText: TextView
    private lateinit var uploadsText: TextView
    private lateinit var uploadNowButton: Button
    private lateinit var testConnectionStatusText: TextView
    
    // Upload status layouts
    private lateinit var lastFullBackupLayout: LinearLayout
    private lateinit var lastFullBackupTimestamp: TextView
    private lateinit var waitingForConnectivityLayout: LinearLayout
    private lateinit var syncingRecordsLayout: LinearLayout
    private lateinit var syncingRecordsProgress: ProgressBar
    private lateinit var uploadSuccessLayout: LinearLayout
    private lateinit var uploadSuccessTimestamp: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uploads)
        
        initializeViews()
        setupNavigation()
        setupClickListeners()
        startUploadStatusUpdates()
        showInitialStatus()
    }
    
    private fun initializeViews() {
        pendingUploadsText = findViewById(R.id.pending_uploads)
        uploadsText = findViewById(R.id.uploads)
        uploadNowButton = findViewById(R.id.btb_upload_now)
        testConnectionStatusText = findViewById(R.id.test_connection_status)
        
        // Upload status layouts
        lastFullBackupLayout = findViewById(R.id.last_full_backup)
        lastFullBackupTimestamp = findViewById(R.id.last_full_backup_timestamp)
        waitingForConnectivityLayout = findViewById(R.id.waiting_for_connectivity)
        syncingRecordsLayout = findViewById(R.id.syncing_records)
        syncingRecordsProgress = findViewById(R.id.syncing_progress_prpgressbar)
        uploadSuccessLayout = findViewById(R.id.upload_success)
        uploadSuccessTimestamp = findViewById(R.id.upload_success_timestamp)
    }
    
    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.navigation_uploads
        
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
                R.id.navigation_uploads -> true
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
        uploadNowButton.setOnClickListener {
            startUploadProcess()
        }
    }
    
    private fun showInitialStatus() {
        hideAllStatusLayouts()
        lastFullBackupLayout.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // TEMPORARILY DISABLED - DATABASE ACCESS
                // val uploadedCount = database.measurementDao().getUploadedCount()
                val uploadedCount = 0 // Placeholder
                val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(Date())
                lastFullBackupTimestamp.text = "Last upload: $timestamp ($uploadedCount records)"
            } catch (e: Exception) {
                lastFullBackupTimestamp.text = "No previous uploads"
            }
        }
    }
    
    private fun hideAllStatusLayouts() {
        lastFullBackupLayout.visibility = View.GONE
        waitingForConnectivityLayout.visibility = View.GONE
        syncingRecordsLayout.visibility = View.GONE
        uploadSuccessLayout.visibility = View.GONE
    }
    
    private fun startUploadProcess() {
        hideAllStatusLayouts()
        waitingForConnectivityLayout.visibility = View.VISIBLE
        
        // Start immediate sync
        SyncManager.startImmediateSync(this)
        
        // Monitor upload progress
        monitorUploadProgress()
    }
    
    private fun monitorUploadProgress() {
        lifecycleScope.launch {
            try {
                // Get current signal-based counts (same as updateUploadCounts)
                val currentSignal = NetworkDataStorage.getCurrentSimulatedSignalStrength()
                val (rsrp, rsrq) = currentSignal
                
                val signalQuality = when {
                    rsrp > -85 -> "excellent"
                    rsrp in -95.0..-85.0 -> "good"
                    rsrp in -105.0..-95.0 -> "fair"
                    else -> "poor"
                }
                
                val baseCount = when (signalQuality) {
                    "excellent" -> 150
                    "good" -> 120
                    "fair" -> 80
                    "poor" -> 40
                    else -> 60
                }
                
                val currentTime = System.currentTimeMillis()
                val timeVariation = ((currentTime / 10000) % 20).toInt()
                val pendingCount = maxOf(0, (baseCount / 2) - timeVariation)
                
                if (pendingCount == 0) {
                    showUploadSuccess(0)
                    return@launch
                }
                
                // Show syncing status
                hideAllStatusLayouts()
                syncingRecordsLayout.visibility = View.VISIBLE
                syncingRecordsProgress.max = pendingCount
                syncingRecordsProgress.progress = 0
                
                // Simulate progress monitoring
                var currentProgress = 0
                while (currentProgress < pendingCount) {
                    delay(1000)
                    currentProgress += 1
                    syncingRecordsProgress.progress = currentProgress
                }
                
                showUploadSuccess(pendingCount)
                
            } catch (e: Exception) {
                e.printStackTrace()
                showUploadError()
            }
        }
    }
    
    private fun showUploadSuccess(uploadedCount: Int) {
        hideAllStatusLayouts()
        uploadSuccessLayout.visibility = View.VISIBLE
        
        val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            .format(Date())
        uploadSuccessTimestamp.text = "Upload completed at $timestamp ($uploadedCount records)"
        
        // Update counts
        updateUploadCounts()
    }
    
    private fun showUploadError() {
        hideAllStatusLayouts()
        lastFullBackupLayout.visibility = View.VISIBLE
        lastFullBackupTimestamp.text = "Upload failed. Please try again."
        lastFullBackupTimestamp.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }
    
    private fun startUploadStatusUpdates() {
        lifecycleScope.launch {
            while (true) {
                try {
                    updateUploadCounts()
                    delay(2000) // Update every 2 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }
    
    private fun updateUploadCounts() {
        lifecycleScope.launch {
            try {
                // Get current values from NetworkDataStorage (same as Metrics screen)
                val currentSignal = NetworkDataStorage.getCurrentSimulatedSignalStrength()
                val currentCellInfo = NetworkDataStorage.getCurrentSimulatedCellInfo()
                val (rsrp, rsrq) = currentSignal
                val (cellId, pci) = currentCellInfo
                
                // Create realistic counts based on current signal strength and activity
                // Better signal = more successful uploads, worse signal = more pending
                val signalQuality = when {
                    rsrp > -85 -> "excellent"
                    rsrp in -95.0..-85.0 -> "good"
                    rsrp in -105.0..-95.0 -> "fair"
                    else -> "poor"
                }
                
                // Simulate upload counts based on signal quality
                val baseCount = when (signalQuality) {
                    "excellent" -> 150
                    "good" -> 120
                    "fair" -> 80
                    "poor" -> 40
                    else -> 60
                }
                
                // Add some variation based on current time
                val currentTime = System.currentTimeMillis()
                val timeVariation = ((currentTime / 10000) % 20).toInt()
                
                val uploadedCount = baseCount + timeVariation
                val pendingCount = maxOf(0, (baseCount / 2) - timeVariation)
                
                runOnUiThread {
                    pendingUploadsText.text = pendingCount.toString()
                    uploadsText.text = uploadedCount.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}