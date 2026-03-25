import { useState } from 'react';
import './StreamUrlDisplay.css';

interface StreamUrlDisplayProps {
  /** Full streaming URL */
  url: string;
  /** wifi | hotspot | none */
  connectionType?: 'wifi' | 'hotspot' | 'none' | null;
  /** WiFi network name */
  networkSsid?: string | null;
  /** Device IP address */
  ipAddress?: string | null;
  /** Whether copying is enabled */
  enableCopy?: boolean;
}

/**
 * Displays the streaming URL prominently for OBS browser source.
 */
export function StreamUrlDisplay({
  url,
  connectionType,
  networkSsid,
  enableCopy = true
}: StreamUrlDisplayProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('Failed to copy URL:', error);
    }
  };

  return (
    <div className="stream-url-display">
      <label className="url-label">
        OBS Browser Source URL:
      </label>
      
      <div className="network-info">
        {connectionType === 'wifi' && networkSsid ? (
          <>
            <span className="network-icon">📶</span>
            <span>OBS must be on <strong>{networkSsid}</strong> WiFi</span>
          </>
        ) : connectionType === 'hotspot' ? (
          <>
            <span className="network-icon">📡</span>
            <span>Connect your OBS computer to your phone's hotspot</span>
          </>
        ) : null}
      </div>
      
      <div className="url-container">
        <code className="url-text">{url}</code>
        
        {enableCopy && (
          <button 
            className="copy-button" 
            onClick={handleCopy}
            aria-label="Copy URL to clipboard"
          >
            {copied ? (
              <span className="copy-text copied">✓ Copied!</span>
            ) : (
              <span className="copy-text">Copy URL</span>
            )}
          </button>
        )}
      </div>
      
      <p className="hint">
        Paste this URL into OBS as a Browser source
      </p>
    </div>
  );
}
