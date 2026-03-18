import React from 'react';
import './CameraSwitchButton.css';

interface CameraSwitchButtonProps {
  /** Callback when switch is clicked */
  onSwitch: () => void;
  /** Current camera type */
  currentCamera: 'front' | 'back';
  /** Whether currently switching */
  isSwitching?: boolean;
  /** Disabled state */
  disabled?: boolean;
}

/**
 * Button to switch between front and rear cameras.
 */
export function CameraSwitchButton({ 
  onSwitch, 
  currentCamera,
  isSwitching = false,
  disabled = false 
}: CameraSwitchButtonProps) {
  const handleClick = () => {
    if (!isSwitching && !disabled) {
      onSwitch();
    }
  };

  const icon = currentCamera === 'front' ? '🤳' : '📷';
  const label = currentCamera === 'front' ? 'Front Camera' : 'Rear Camera';

  return (
    <button
      className="camera-switch-button"
      onClick={handleClick}
      disabled={disabled || isSwitching}
      aria-label={`Switch from ${label}`}
    >
      <span className="button-content">
        <span className="icon">{icon}</span>
        <span className="label">{label}</span>
        {isSwitching && <span className="spinner"></span>}
      </span>
    </button>
  );
}
