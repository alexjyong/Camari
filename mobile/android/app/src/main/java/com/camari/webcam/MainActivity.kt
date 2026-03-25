package com.camari.webcam

import android.os.Bundle
import android.view.WindowManager
import com.getcapacitor.BridgeActivity
import com.camari.streaming.CameraStreamPlugin

class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        // Clean up the streaming service when the activity is finishing (user explicitly closed
        // the app). If the activity is just being recreated (rotation etc.), isFinishing is false
        // and we leave the foreground service running.
        if (isFinishing) {
            bridge.getPlugin("CameraStream")
                ?.let { it.instance as? CameraStreamPlugin }
                ?.cleanup()
        }
        super.onDestroy()
    }

    override fun load() {
        registerPlugin(CameraStreamPlugin::class.java)
        super.load()
    }
}
