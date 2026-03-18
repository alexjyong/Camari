package com.camari.webcam

import android.os.Bundle
import android.view.WindowManager
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Cleanup streaming resources
    }

    override fun onPause() {
        super.onPause()
        // TODO: Optionally pause streaming when app is backgrounded
    }

    override fun onResume() {
        super.onResume()
        // TODO: Optionally resume streaming when app returns to foreground
    }
}
