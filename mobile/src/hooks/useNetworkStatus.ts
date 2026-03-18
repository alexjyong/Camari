import { useState, useEffect, useCallback } from 'react';
import { CameraStream } from '../services/CameraStreamService';
import { NetworkStatusState, DEFAULT_NETWORK_STATUS, getConnectionQuality } from '../types/network';

interface UseNetworkStatusOptions {
  /** Polling interval for status updates (ms) */
  pollInterval?: number;
  /** Callback when network status changes */
  onStatusChange?: (status: NetworkStatusState) => void;
  /** Callback when reconnection timeout occurs */
  onReconnectionTimeout?: () => void;
}

interface UseNetworkStatusReturn {
  /** Current network status */
  status: NetworkStatusState;
  /** Whether currently connected to WiFi */
  isConnected: boolean;
  /** Whether currently reconnecting */
  isReconnecting: boolean;
  /** Connection quality label */
  qualityLabel: string | null;
  /** Refresh network status */
  refreshStatus: () => Promise<void>;
}

/**
 * Hook for monitoring WiFi network status.
 */
export function useNetworkStatus(options: UseNetworkStatusOptions = {}): UseNetworkStatusReturn {
  const {
    pollInterval = 2000,
    onStatusChange,
    onReconnectionTimeout,
  } = options;

  const [status, setStatus] = useState<NetworkStatusState>(DEFAULT_NETWORK_STATUS);

  /**
   * Refresh network status from plugin.
   */
  const refreshStatus = useCallback(async () => {
    try {
      const pluginStatus = await CameraStream.getStatus();
      
      setStatus(prev => {
        const newStatus: NetworkStatusState = {
          isConnected: pluginStatus.status !== 'reconnecting' && pluginStatus.ipAddress !== null,
          ssid: pluginStatus.networkSsid,
          ipAddress: pluginStatus.ipAddress,
          signalStrength: null, // Would need native implementation
          isReconnecting: pluginStatus.status === 'reconnecting',
          reconnectTimeoutAt: null, // Would need native implementation
          quality: pluginStatus.ipAddress ? getConnectionQuality(-60) : null, // Estimate
        };
        
        // Check for reconnection timeout
        if (prev.isReconnecting && !newStatus.isReconnecting && !newStatus.isConnected) {
          onReconnectionTimeout?.();
        }
        
        return newStatus;
      });
    } catch (error) {
      console.error('Error getting network status:', error);
    }
  }, [onReconnectionTimeout]);

  // Poll for status updates
  useEffect(() => {
    refreshStatus();
    
    const intervalId = setInterval(refreshStatus, pollInterval);
    
    return () => clearInterval(intervalId);
  }, [refreshStatus, pollInterval]);

  // Notify on status change
  useEffect(() => {
    onStatusChange?.(status);
  }, [status, onStatusChange]);

  const qualityLabel = status.quality 
    ? `${status.quality.charAt(0).toUpperCase() + status.quality.slice(1)} signal`
    : null;

  return {
    status,
    isConnected: status.isConnected,
    isReconnecting: status.isReconnecting,
    qualityLabel,
    refreshStatus,
  };
}
