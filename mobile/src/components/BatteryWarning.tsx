import './BatteryWarning.css';

interface BatteryWarningProps {
  /** Current battery level (0-100) */
  batteryLevel: number;
  /** Whether device is charging */
  isCharging: boolean;
  /** Low battery threshold (default 20%) */
  threshold?: number;
  /** Whether to show the warning */
  showWarning?: boolean;
}

/**
 * Shows battery level and warning when battery is low.
 */
export function BatteryWarning({ 
  batteryLevel, 
  isCharging,
  threshold = 20,
  showWarning = true 
}: BatteryWarningProps) {
  const isLow = batteryLevel < threshold;
  const shouldShow = showWarning && isLow && !isCharging;

  if (!shouldShow) return null;

  return (
    <div className="battery-warning">
      <span className="battery-icon">
        {batteryLevel < 10 ? '🪫' : '🔋'}
      </span>
      <div className="battery-content">
        <span className="battery-level">{batteryLevel}%</span>
        <span className="battery-message">
          Battery low - recommend plugging in device
        </span>
      </div>
    </div>
  );
}
