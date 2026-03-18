/**
 * TypeScript types for the CameraStream Capacitor plugin.
 */

export interface CameraStreamPlugin {
  /**
   * Start streaming from the camera.
   * Returns the stream URL for OBS browser source.
   */
  startStreaming(): Promise<StartStreamingResult>;
  
  /**
   * Stop streaming and release resources.
   */
  stopStreaming(): Promise<void>;
  
  /**
   * Switch between front and rear cameras.
   */
  switchCamera(): Promise<SwitchCameraResult>;
  
  /**
   * Get current streaming status.
   */
  getStatus(): Promise<GetStatusResult>;
  
  /**
   * Add event listener for plugin events.
   */
  addListener(eventName: string, listenerFunc: (...args: any[]) => any): Promise<PluginListenerHandle>;
}

export interface StartStreamingResult {
  /** Full streaming URL for OBS browser source */
  streamUrl: string;
  /** Device IP address on local network */
  ipAddress: string;
  /** HTTP server port */
  port: number;
  /** WiFi network name (SSID) */
  networkSsid: string;
  /** Active camera type */
  cameraType: 'front' | 'back';
}

export interface SwitchCameraResult {
  /** New active camera type after switch */
  cameraType: 'front' | 'back';
  /** Whether switch was successful */
  success: boolean;
}

export interface GetStatusResult {
  /** Current streaming state */
  status: 'idle' | 'starting' | 'streaming' | 'reconnecting' | 'stopped' | 'error';
  /** Current camera type (null if not streaming) */
  cameraType: 'front' | 'back' | null;
  /** Current battery level (0-100) */
  batteryLevel: number;
  /** Whether device is charging */
  isCharging: boolean;
  /** Whether battery is low (<20%) */
  isLowBattery: boolean;
  /** WiFi network name */
  networkSsid: string | null;
  /** Device IP address */
  ipAddress: string | null;
  /** Error message if status is 'error' */
  errorMessage?: string;
}

export interface BatteryWarningEvent {
  batteryLevel: number;
  isCharging: boolean;
  message: string;
}

export interface NetworkStatusEvent {
  isConnected: boolean;
  networkSsid: string | null;
  ipAddress: string | null;
  isReconnecting: boolean;
}

export interface StreamErrorEvent {
  errorCode: string;
  message: string;
  recoverable: boolean;
}

export interface PluginListenerHandle {
  remove: () => void;
}
