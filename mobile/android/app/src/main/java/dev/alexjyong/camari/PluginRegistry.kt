package dev.alexjyong.camari

import com.getcapacitor.Plugin
import java.util.*

/**
 * Plugin registration for Capacitor.
 * This file registers all custom plugins used in the app.
 */
class PluginRegistry {
    
    companion object {
        /**
         * Get list of plugin classes to register with Capacitor.
         */
        fun getPlugins(): Set<Class<out Plugin>> {
            return setOf(
                dev.alexjyong.camari.streaming.CameraStreamPlugin::class.java
            )
        }
    }
}
