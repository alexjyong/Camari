/**
 * Network status types.
 */

export type ConnectionQuality = 'excellent' | 'good' | 'fair' | 'poor';

export interface NetworkStatusState {
  /** Whether device is connected to WiFi */
  isConnected: boolean;
  /** WiFi network name (SSID) */
  ssid: string | null;
  /** Device IP address */
  ipAddress: string | null;
  /** WiFi signal strength in dBm */
  signalStrength: number | null;
  /** Whether currently attempting reconnection */
  isReconnecting: boolean;
  /** Reconnection timeout timestamp */
  reconnectTimeoutAt: number | null;
  /** Connection quality level */
  quality: ConnectionQuality | null;
}

export const DEFAULT_NETWORK_STATUS: NetworkStatusState = {
  isConnected: false,
  ssid: null,
  ipAddress: null,
  signalStrength: null,
  isReconnecting: false,
  reconnectTimeoutAt: null,
  quality: null,
};

/**
 * Calculate connection quality from signal strength (dBm).
 */
export function getConnectionQuality(rssi: number | null): ConnectionQuality | null {
  if (rssi === null) return null;
  
  if (rssi >= -50) return 'excellent';
  if (rssi >= -65) return 'good';
  if (rssi >= -75) return 'fair';
  return 'poor';
}
