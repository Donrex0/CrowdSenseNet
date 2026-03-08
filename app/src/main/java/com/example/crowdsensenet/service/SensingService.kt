package com.example.crowdsensenet.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.crowdsensenet.R
// import com.example.crowdsensenet.data.local.AppDatabase  // TEMPORARILY DISABLED
// import com.example.crowdsensenet.data.local.MeasurementEntity  // TEMPORARILY DISABLED
import com.example.crowdsensenet.utils.LocationUtils
import com.example.crowdsensenet.utils.NetworkUtils
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class SensingService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    // private lateinit var database: AppDatabase  // TEMPORARILY DISABLED
    private var isRunning = false
    private var sensingJob: Job? = null
    private var samplingIntervalMs = 5000L // Default 5 seconds

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SAMPLING_INTERVAL = "EXTRA_SAMPLING_INTERVAL"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "SensingChannel"
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // database = AppDatabase.getDatabase(this)  // TEMPORARILY DISABLED
        LocationUtils.initialize(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                samplingIntervalMs = intent.getLongExtra(EXTRA_SAMPLING_INTERVAL, 5000L)
                startSensing()
            }
            ACTION_STOP -> {
                stopSensing()
                stopSelf()
            }
        }
        
        return START_STICKY
    }

    private fun startSensing() {
        if (isRunning) return
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotification()
        } else {
            createNotificationLegacy()
        }
        
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true
        
        sensingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    collectMeasurement()
                    delay(samplingIntervalMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(samplingIntervalMs) // Continue even if error occurs
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun collectMeasurement() {
        if (!hasRequiredPermissions()) return

        // Get network information - check API level for each call
        val networkType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkUtils.getNetworkType(this)
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
        
        val (cellId, pci) = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                NetworkUtils.getCellInfo(this)
            } else {
                Pair("Unknown", 0.0)
            }
        } catch (e: Exception) {
            Pair("Unknown", 0.0)
        }
        
        // Get signal strength
        val (rsrp, rsrq) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use modern API for Android 10+
            getSignalStrengthModern()
        } else {
            // Use legacy API for older versions
            NetworkUtils.getSignalStrengthLegacy(this)
        }
        
        // Get location
        val location = LocationUtils.getCurrentLocation(this)
        
        // TEMPORARILY DISABLED - Create measurement entity
        /*
        val measurement = MeasurementEntity(
            deviceId = getDeviceIdentifier(),
            timestamp = System.currentTimeMillis(),
            rsrp = rsrp,
            rsrq = rsrq,
            pci = pci,
            cellId = cellId,
            networkTechnology = networkType,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            isUploaded = false
        )
        */
        
        // TEMPORARILY DISABLED - Save to database
        // database.measurementDao().insert(measurement)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun getSignalStrengthModern(): Pair<Double, Double> {
        return suspendCancellableCoroutine { cont ->
            val phoneStateListener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val result = NetworkUtils.getSignalStrength(signalStrength)
                    cont.resume(result)
                    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE)
                }
            }
            
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            
            // Handle cancellation
            cont.invokeOnCancellation {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun stopSensing() {
        isRunning = false
        sensingJob?.cancel()
        telephonyManager.listen(object : PhoneStateListener() {}, PhoneStateListener.LISTEN_NONE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensing()
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getDeviceIdentifier(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sensing Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Network measurement collection service"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrowdSenseNet")
            .setContentText("Collecting network measurements")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun createNotificationLegacy(): Notification {
        return NotificationCompat.Builder(this)
            .setContentTitle("CrowdSenseNet")
            .setContentText("Collecting network measurements")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
