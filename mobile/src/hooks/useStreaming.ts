import { useState, useEffect, useCallback, useRef } from 'react';
import { CameraStream } from '../services/CameraStreamService';
import { StreamingSession, StreamingStatus } from '../types/streaming';
import { CameraType } from '../types/camera';

interface UseStreamingOptions {
  /** Callback when streaming starts */
  onStreamingStart?: (session: StreamingSession) => void;
  /** Callback when streaming stops */
  onStreamingStop?: () => void;
  /** Callback when error occurs */
  onError?: (error: Error) => void;
  /** Polling interval for status updates (ms) */
  statusPollInterval?: number;
}

interface UseStreamingReturn {
  /** Current streaming session */
  session: StreamingSession | null;
  /** Whether currently streaming */
  isStreaming: boolean;
  /** Whether OBS is currently connected to the stream */
  isObsConnected: boolean;
  /** Whether currently starting */
  isStarting: boolean;
  /** Whether currently stopping */
  isStopping: boolean;
  /** Start streaming */
  startStreaming: () => Promise<void>;
  /** Stop streaming */
  stopStreaming: () => Promise<void>;
  /** Clear error state */
  clearError: () => void;
  /** Refresh session status */
  refreshStatus: () => Promise<void>;
}

/**
 * Create initial streaming session state.
 */
function createInitialSession(): StreamingSession {
  return {
    sessionId: crypto.randomUUID(),
    status: 'idle',
    streamUrl: null,
    ipAddress: null,
    port: null,
    cameraType: null,
    startedAt: null,
    batteryLevel: 0,
    networkSsid: null,
    obsConnected: false,
    errorMessage: null,
  };
}

/**
 * Hook for managing streaming session.
 */
export function useStreaming(options: UseStreamingOptions = {}): UseStreamingReturn {
  const {
    onStreamingStart,
    onStreamingStop,
    onError,
    statusPollInterval = 1000,
  } = options;

  const [session, setSession] = useState<StreamingSession | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const pollIntervalRef = useRef<number | null>(null);

  /**
   * Refresh streaming status from native plugin.
   */
  const refreshStatus = useCallback(async () => {
    try {
      const status = await CameraStream.getStatus();
      
      setSession(prev => {
        if (!prev) return prev;
        
        const newStatus: StreamingStatus = status.status as StreamingStatus;
        
        return {
          ...prev,
          status: newStatus,
          cameraType: status.cameraType,
          batteryLevel: status.batteryLevel,
          isCharging: status.isCharging,
          isLowBattery: status.isLowBattery,
          networkSsid: status.networkSsid,
          ipAddress: status.ipAddress,
          obsConnected: status.obsConnected ?? false,
          errorMessage: status.errorMessage || null,
        };
      });
    } catch (error) {
      console.error('Error refreshing status:', error);
    }
  }, []);

  /**
   * Start streaming session.
   */
  const startStreaming = useCallback(async () => {
    if (isStarting || session?.status === 'streaming') return;

    setIsStarting(true);

    try {
      const result = await CameraStream.startStreaming();
      
      const newSession: StreamingSession = {
        sessionId: crypto.randomUUID(),
        status: 'streaming',
        streamUrl: result.streamUrl,
        ipAddress: result.ipAddress,
        port: result.port,
        cameraType: result.cameraType as CameraType,
        startedAt: Date.now(),
        batteryLevel: 0,
        networkSsid: result.networkSsid,
        obsConnected: false,
        errorMessage: null,
      };

      setSession(newSession);
      onStreamingStart?.(newSession);

      // Start polling for status updates
      pollIntervalRef.current = window.setInterval(refreshStatus, statusPollInterval);
      
    } catch (error) {
      console.error('Failed to start streaming:', error);
      
      const errorSession: StreamingSession = {
        ...createInitialSession(),
        status: 'error',
        errorMessage: error instanceof Error ? error.message : 'Unknown error',
      };
      
      setSession(errorSession);
      onError?.(error as Error);
    } finally {
      setIsStarting(false);
    }
  }, [isStarting, session?.status, onStreamingStart, onError, refreshStatus, statusPollInterval]);

  /**
   * Stop streaming session.
   */
  const stopStreaming = useCallback(async () => {
    if (!session || session.status !== 'streaming') return;

    setIsStopping(true);

    try {
      await CameraStream.stopStreaming();

      // Stop polling
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }

      setSession(prev => prev ? { ...prev, status: 'stopped' } : null);

      // Reset session after brief pause so "Stopping..." is visible
      setTimeout(() => {
        setSession(null);
        setIsStopping(false);
        onStreamingStop?.();
      }, 500);

    } catch (error) {
      console.error('Failed to stop streaming:', error);
      setIsStopping(false);
      onError?.(error as Error);
    }
  }, [session, onStreamingStop, onError]);

  /**
   * Clear error state.
   */
  const clearError = useCallback(() => {
    setSession(prev => prev ? {
      ...prev,
      status: 'idle',
      errorMessage: null,
    } : null);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, []);

  return {
    session,
    isStreaming: session?.status === 'streaming',
    isObsConnected: session?.status === 'streaming' && (session?.obsConnected ?? false),
    isStarting,
    isStopping,
    startStreaming,
    stopStreaming,
    clearError,
    refreshStatus,
  };
}
