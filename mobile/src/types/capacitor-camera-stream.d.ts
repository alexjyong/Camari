/**
 * Type declarations for the custom CameraStream Capacitor plugin.
 */

export interface CameraStreamPlugin {
  startStreaming(): Promise<StartStreamingResult>;
  stopStreaming(): Promise<void>;
  switchCamera(): Promise<SwitchCameraResult>;
  setOrientation(options: { degrees: number }): Promise<void>;
  getStatus(): Promise<GetStatusResult>;
  addListener(eventName: string, listenerFunc: (...args: any[]) => any): Promise<PluginListenerHandle>;
}

export interface StartStreamingResult {
  streamUrl: string;
  ipAddress: string;
  port: number;
  networkSsid: string;
  cameraType: 'front' | 'back';
}

export interface SwitchCameraResult {
  cameraType: 'front' | 'back';
  success: boolean;
}

export interface GetStatusResult {
  status: 'idle' | 'starting' | 'streaming' | 'reconnecting' | 'stopped' | 'error';
  cameraType: 'front' | 'back' | null;
  batteryLevel: number;
  isCharging: boolean;
  isLowBattery: boolean;
  networkSsid: string | null;
  ipAddress: string | null;
  errorMessage?: string;
}

export interface PluginListenerHandle {
  remove: () => void;
}
