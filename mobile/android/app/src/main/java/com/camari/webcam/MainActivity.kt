package com.camari.webcam

import android.os.Bundle
import android.view.WindowManager
import com.getcapacitor.BridgeActivity
import com.getcapacitor.Plugin

class MainActivity : BridgeActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: Plugin resources will be cleaned up by garbage collection
        // For proper cleanup, the plugin should be registered with Capacitor
        android.util.Log.i("MainActivity", "Activity destroyed")
    }

    override fun onPause() {
        super.onPause()
        // Optionally pause streaming when app is backgrounded
    }

    override fun onResume() {
        super.onResume()
        // Optionally resume streaming when app returns to foreground
    }
    
    override fun load() {
        // Register custom Capacitor plugins
        registerPlugin(com.camari.streaming.CameraStreamPlugin::class.java)
        super.load()
    }
}
