import React from 'react';
import './StartStreamingButton.css';

interface StartStreamingButtonProps {
  /** Callback when start is clicked */
  onStart: () => void;
  /** Whether currently starting */
  isStarting?: boolean;
  /** Disabled state */
  disabled?: boolean;
}

/**
 * Main button to start streaming.
 */
export function StartStreamingButton({ 
  onStart, 
  isStarting = false,
  disabled = false 
}: StartStreamingButtonProps) {
  const handleClick = () => {
    onStart();
  };

  return (
    <button
      className="start-streaming-button"
      onClick={handleClick}
      disabled={disabled || isStarting}
      aria-label="Start streaming to OBS"
    >
      {isStarting ? (
        <span className="button-content">
          <span className="spinner"></span>
          Starting...
        </span>
      ) : (
        <span className="button-content">
          <span className="icon">📹</span>
          Start Streaming
        </span>
      )}
    </button>
  );
}
