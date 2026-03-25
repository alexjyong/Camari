import { useEffect, useState } from 'react';
import './StreamingIndicator.css';

interface StreamingIndicatorProps {
  /** When streaming started (timestamp) */
  startedAt: number | null;
  /** Whether currently streaming */
  isStreaming: boolean;
}

/**
 * Shows recording indicator and streaming duration timer.
 */
export function StreamingIndicator({ 
  startedAt, 
  isStreaming 
}: StreamingIndicatorProps) {
  const [duration, setDuration] = useState(0);

  useEffect(() => {
    if (!isStreaming || !startedAt) {
      setDuration(0);
      return;
    }

    const interval = setInterval(() => {
      setDuration(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);

    return () => clearInterval(interval);
  }, [isStreaming, startedAt]);

  const formatDuration = (seconds: number): string => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hrs > 0) {
      return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (!isStreaming) return null;

  return (
    <div className="streaming-indicator">
      <div className="status-row">
        <span className="recording-dot"></span>
        <span className="status-text">Streaming</span>
      </div>
      <div className="duration">{formatDuration(duration)}</div>
    </div>
  );
}
