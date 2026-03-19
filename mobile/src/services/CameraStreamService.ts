import { registerPlugin } from '@capacitor/core';
import type { CameraStreamPlugin } from '../types/capacitor-camera-stream';

/**
 * Register the custom CameraStream Capacitor plugin.
 * This connects to the native Kotlin implementation.
 */
export const CameraStream = registerPlugin<CameraStreamPlugin>('CameraStream', {
  web: () => {
    // Web implementation (stub for development)
    return {
      async startStreaming() {
        console.log('[CameraStream.web] startStreaming (stub)');
        return {
          streamUrl: 'http://192.168.1.100:8080/stream',
          ipAddress: '192.168.1.100',
          port: 8080,
          networkSsid: 'TestWiFi',
          cameraType: 'front' as const,
        };
      },
      async stopStreaming() {
        console.log('[CameraStream.web] stopStreaming (stub)');
      },
      async switchCamera() {
        console.log('[CameraStream.web] switchCamera (stub)');
        return { cameraType: 'back' as const, success: true };
      },
      async setOrientation({ degrees }: { degrees: number }) {
        console.log('[CameraStream.web] setOrientation (stub):', degrees);
      },
      async getStatus() {
        return {
          status: 'idle' as const,
          cameraType: null,
          batteryLevel: 100,
          isCharging: false,
          isLowBattery: false,
          networkSsid: null,
          ipAddress: null,
        };
      },
      async addListener(eventName: string, listenerFunc: (...args: any[]) => any) {
        console.log('[CameraStream.web] addListener:', eventName);
        return { remove: () => {} };
      },
    };
  },
});
