import React from 'react';
import './StopStreamingButton.css';

interface StopStreamingButtonProps {
  /** Callback when stop is clicked */
  onStop: () => void;
  /** Whether currently stopping */
  isStopping?: boolean;
  /** Disabled state */
  disabled?: boolean;
  /** Show confirmation dialog */
  requireConfirmation?: boolean;
}

/**
 * Button to stop streaming.
 */
export function StopStreamingButton({ 
  onStop, 
  isStopping = false,
  disabled = false,
  requireConfirmation = false 
}: StopStreamingButtonProps) {
  const [showConfirm, setShowConfirm] = React.useState(false);

  const handleClick = () => {
    if (requireConfirmation && !showConfirm) {
      setShowConfirm(true);
      // Auto-hide confirmation after 3 seconds
      setTimeout(() => setShowConfirm(false), 3000);
      return;
    }
    
    onStop();
    setShowConfirm(false);
  };

  const handleCancel = () => {
    setShowConfirm(false);
  };

  if (showConfirm) {
    return (
      <div className="stop-confirmation">
        <p className="confirm-text">Stop streaming?</p>
        <div className="confirm-buttons">
          <button 
            className="confirm-yes"
            onClick={handleClick}
            disabled={isStopping}
          >
            Yes, Stop
          </button>
          <button 
            className="confirm-no"
            onClick={handleCancel}
            disabled={isStopping}
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <button
      className="stop-streaming-button"
      onClick={handleClick}
      disabled={disabled || isStopping}
      aria-label="Stop streaming"
    >
      {isStopping ? (
        <span className="button-content">
          <span className="spinner"></span>
          Stopping...
        </span>
      ) : (
        <span className="button-content">
          <span className="icon">⏹</span>
          Stop Streaming
        </span>
      )}
    </button>
  );
}
