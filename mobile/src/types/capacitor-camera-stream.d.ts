/**
 * Type declarations for the custom CameraStream Capacitor plugin.
 */

export type ResolutionPreset = '480p' | '720p' | '1080p';

export interface CameraStreamPlugin {
  startStreaming(options?: StartStreamingOptions): Promise<StartStreamingResult>;
  stopStreaming(): Promise<void>;
  switchCamera(): Promise<SwitchCameraResult>;
  setOrientation(options: { degrees: number }): Promise<void>;
  getStatus(): Promise<GetStatusResult>;
  openAppSettings(): Promise<void>;
  addListener(eventName: string, listenerFunc: (...args: any[]) => any): Promise<PluginListenerHandle>;
}

export interface StartStreamingOptions {
  resolution?: ResolutionPreset;
}

export interface StartStreamingResult {
  streamUrl: string;
  ipAddress: string;
  port: number;
  networkSsid: string;
  cameraType: 'front' | 'back';
  resolution?: string;
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
  connectionType: 'wifi' | 'hotspot' | 'none';
  networkSsid: string | null;
  ipAddress: string | null;
  obsConnected?: boolean;
  resolution?: string;
  errorMessage?: string;
}

export interface PluginListenerHandle {
  remove: () => void;
}
