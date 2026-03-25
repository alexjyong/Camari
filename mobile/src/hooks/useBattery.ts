import { useState, useEffect, useCallback } from 'react';
import { CameraStream } from '../services/CameraStreamService';

interface UseBatteryOptions {
  /** Callback when battery becomes low */
  onLowBattery?: (level: number) => void;
  /** Low battery threshold (default 20%) */
  lowBatteryThreshold?: number;
}

interface UseBatteryReturn {
  /** Current battery level (0-100) */
  level: number;
  /** Whether device is charging */
  isCharging: boolean;
  /** Whether battery is below threshold */
  isLow: boolean;
  /** Battery status text */
  statusText: string;
  /** Refresh battery level */
  refreshLevel: () => Promise<void>;
}

/**
 * Hook for monitoring battery status.
 */
export function useBattery(options: UseBatteryOptions = {}): UseBatteryReturn {
  const {
    onLowBattery,
    lowBatteryThreshold = 20,
  } = options;

  const [level, setLevel] = useState(100);
  const [isCharging, setIsCharging] = useState(false);
  const [wasLow, setWasLow] = useState(false);

  /**
   * Refresh battery level from plugin.
   */
  const refreshLevel = useCallback(async () => {
    try {
      const status = await CameraStream.getStatus();
      setLevel(status.batteryLevel);
      setIsCharging(status.isCharging);
    } catch (error) {
      console.error('Error getting battery status:', error);
    }
  }, []);

  // Initial fetch and polling
  useEffect(() => {
    refreshLevel();
    
    const intervalId = setInterval(refreshLevel, 5000); // Check every 5 seconds
    
    return () => clearInterval(intervalId);
  }, [refreshLevel]);

  // Notify on low battery
  useEffect(() => {
    const isLow = level < lowBatteryThreshold;
    
    if (isLow && !wasLow && !isCharging) {
      onLowBattery?.(level);
      setWasLow(true);
    } else if (!isLow) {
      setWasLow(false);
    }
  }, [level, isCharging, wasLow, onLowBattery, lowBatteryThreshold]);

  const statusText = isCharging
    ? `Charging (${level}%)`
    : level < lowBatteryThreshold
      ? `Low Battery (${level}%) - Recommend plugging in`
      : `Battery: ${level}%`;

  return {
    level,
    isCharging,
    isLow: level < lowBatteryThreshold,
    statusText,
    refreshLevel,
  };
}
