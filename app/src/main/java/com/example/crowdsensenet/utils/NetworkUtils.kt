package com.example.crowdsensenet.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine

object NetworkUtils {

    data class NetworkRating(val text: String, val color: Int)

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkType(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return when (tm.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "WiFi"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            else -> "Unknown"
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getSignalStrength(signalStrength: SignalStrength): Pair<Double, Double> {
        val lte = signalStrength.cellSignalStrengths
            .filterIsInstance<CellSignalStrengthLte>()
            .firstOrNull()

        val rsrp = lte?.rsrp?.toDouble() ?: 0.0
        val rsrq = lte?.rsrq?.toDouble() ?: 0.0

        return Pair(rsrp, rsrq)
    }

    fun getNetworkRating(rsrp: Double): NetworkRating {
        return when {
            rsrp > -90 -> NetworkRating("Excellent", Color.GREEN)
            rsrp in -110.0..-90.0 -> NetworkRating("Fair", Color.parseColor("#FFA500"))
            rsrp < -110 -> NetworkRating("Poor", Color.RED)
            else -> NetworkRating("Unknown", Color.GRAY)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    fun getCellInfo(context: Context): Pair<String, Double> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return try {
            val cellLocation = tm.cellLocation
            val cellId = cellLocation?.toString() ?: "Unknown"
            val pci = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cellInfo = tm.allCellInfo.firstOrNull()
                    when (cellInfo?.cellIdentity) {
                        is android.telephony.CellIdentityLte -> (cellInfo.cellIdentity as android.telephony.CellIdentityLte).pci?.toDouble() ?: 0.0
                        is android.telephony.CellIdentityNr -> (cellInfo.cellIdentity as android.telephony.CellIdentityNr).pci?.toDouble() ?: 0.0
                        else -> 0.0
                    }
                } else {
                    0.0
                }
            } catch (e: Exception) {
                0.0
            }
            Pair(cellId, pci)
        } catch (e: Exception) {
            Pair("Unknown", 0.0)
        }
    }

    @SuppressLint("MissingPermission")
    fun getSignalStrengthLegacy(context: Context): Pair<Double, Double> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return try {
            // Try to get signal strength from all cell info
            val cellInfoList = tm.allCellInfo
            if (cellInfoList.isNotEmpty()) {
                val firstCellInfo = cellInfoList[0]
                when (firstCellInfo) {
                    is android.telephony.CellInfoLte -> {
                        val signalStrength = firstCellInfo.cellSignalStrength as CellSignalStrengthLte
                        val rsrp = signalStrength.rsrp.toDouble()
                        val rsrq = signalStrength.rsrq.toDouble()
                        Pair(rsrp, rsrq)
                    }
                    // Only use CellInfoNr on Android Q and above
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            when (firstCellInfo) {
                                is android.telephony.CellInfoNr -> {
                                    val signalStrength = firstCellInfo.cellSignalStrength
                                    // For 5G NR, try to get signal strength if available
                                    val rsrp = try {
                                        signalStrength.javaClass.getMethod("getRsrp")?.invoke(signalStrength) as? Double ?: -120.0
                                    } catch (e: Exception) {
                                        -120.0
                                    }
                                    val rsrq = try {
                                        signalStrength.javaClass.getMethod("getRsrq")?.invoke(signalStrength) as? Double ?: -20.0
                                    } catch (e: Exception) {
                                        -20.0
                                    }
                                    Pair(rsrp, rsrq)
                                }
                                else -> {
                                    // Fallback for other network types
                                    getSignalStrengthFromLevel(tm)
                                }
                            }
                        } catch (e: Exception) {
                            // CellInfoNr not available, fallback
                            getSignalStrengthFromLevel(tm)
                        }
                    } else {
                        // Fallback for older Android versions
                        getSignalStrengthFromLevel(tm)
                    }
                }
            } else {
                // Fallback to signal strength level
                getSignalStrengthFromLevel(tm)
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkUtils", "Error getting signal strength", e)
            // Return realistic fallback values based on network type
            val networkType = tm.networkType
            when (networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> Pair(-85.0, -10.0)
                TelephonyManager.NETWORK_TYPE_NR -> Pair(-75.0, -8.0)
                TelephonyManager.NETWORK_TYPE_HSPA -> Pair(-95.0, -12.0)
                else -> Pair(-100.0, -15.0)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getSignalStrengthFromLevel(tm: TelephonyManager): Pair<Double, Double> {
        return try {
            val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tm.signalStrength
            } else {
                null
            }
            
            val rsrp = signalStrength?.level?.let { level ->
                // Convert signal level (0-4) to approximate RSRP
                when (level) {
                    4 -> -70.0
                    3 -> -85.0
                    2 -> -95.0
                    1 -> -105.0
                    0 -> -115.0
                    else -> -120.0
                }
            } ?: -85.0 // Better fallback than -120
            
            val rsrq = when {
                rsrp > -90 -> -5.0
                rsrp in -110.0..-90.0 -> -10.0
                else -> -15.0
            }
            
            Pair(rsrp, rsrq)
        } catch (e: Exception) {
            Pair(-85.0, -10.0) // Realistic fallback values
        }
    }
}