import React from 'react';
import type { ResolutionPreset } from '../types/capacitor-camera-stream';
import './ResolutionPicker.css';

const PRESETS: ResolutionPreset[] = ['480p', '720p', '1080p'];

interface ResolutionPickerProps {
  value: ResolutionPreset;
  onChange: (value: ResolutionPreset) => void;
  disabled?: boolean;
}

export function ResolutionPicker({ value, onChange, disabled = false }: ResolutionPickerProps) {
  return (
    <div className="resolution-picker" role="group" aria-label="Streaming resolution">
      {PRESETS.map((preset) => (
        <button
          key={preset}
          type="button"
          className={`resolution-option${value === preset ? ' resolution-option--active' : ''}`}
          onClick={() => onChange(preset)}
          disabled={disabled}
          aria-pressed={value === preset}
        >
          {preset}
        </button>
      ))}
    </div>
  );
}
