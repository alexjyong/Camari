package dev.alexjyong.camari.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Monitors battery status and provides battery level information.
 */
class BatteryMonitor(private val context: Context) {
    
    private val batteryReceiver: BroadcastReceiver
    private var currentBatteryLevel = 0
    private var isChargingState = false
    
    companion object {
        private const val TAG = "BatteryMonitor"
        const val LOW_BATTERY_THRESHOLD = 20 // 20%
    }

    init {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                
                if (level != -1 && scale != -1) {
                    val batteryPercent = (level.toFloat() / scale.toFloat()) * 100
                    currentBatteryLevel = batteryPercent.toInt()
                    
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isChargingState = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == BatteryManager.BATTERY_STATUS_FULL
                    
                    Log.d(TAG, "Battery: $currentBatteryLevel%, Charging: $isChargingState")
                }
            }
        }
        
        // Register for battery updates
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            context.registerReceiver(batteryReceiver, filter)
            // Get initial battery level
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            status?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    currentBatteryLevel = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                    val chargingStatus = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isChargingState = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     chargingStatus == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register battery receiver", e)
        }
    }

    /**
     * Get current battery level percentage (0-100).
     */
    fun getBatteryLevel(): Int {
        return currentBatteryLevel
    }

    /**
     * Check if device is currently charging.
     */
    fun isCharging(): Boolean {
        return isChargingState
    }

    /**
     * Check if battery is low (below threshold).
     */
    fun isLowBattery(): Boolean {
        return currentBatteryLevel < LOW_BATTERY_THRESHOLD
    }

    /**
     * Get battery status as a descriptive string.
     */
    fun getBatteryStatusText(): String {
        return when {
            isChargingState -> "Charging ($currentBatteryLevel%)"
            isLowBattery() -> "Low Battery ($currentBatteryLevel%) - Recommend plugging in"
            else -> "Battery: $currentBatteryLevel%"
        }
    }

    /**
     * Unregister the battery receiver.
     */
    fun unregister() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister battery receiver", e)
        }
    }
}
