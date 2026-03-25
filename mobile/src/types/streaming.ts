/**
 * Streaming session state and types.
 */

export type StreamingStatus = 
  | 'idle'
  | 'starting'
  | 'streaming'
  | 'reconnecting'
  | 'stopped'
  | 'error';

export interface StreamingSession {
  /** Unique session identifier */
  sessionId: string;
  /** Current streaming state */
  status: StreamingStatus;
  /** Full URL for OBS browser source */
  streamUrl: string | null;
  /** Device IP address on local network */
  ipAddress: string | null;
  /** HTTP server port */
  port: number | null;
  /** Active camera type */
  cameraType: 'front' | 'back' | null;
  /** When streaming started (Unix timestamp) */
  startedAt: number | null;
  /** Current battery level (0-100) */
  batteryLevel: number;
  /** WiFi network name */
  networkSsid: string | null;
  /** Whether OBS browser source is currently connected to the stream */
  obsConnected: boolean;
  /** Error message if status is 'error' */
  errorMessage: string | null;
}

export interface Resolution {
  width: number;
  height: number;
}

// Default 720p @ 30fps as per spec
export const DEFAULT_RESOLUTION: Resolution = {
  width: 1280,
  height: 720,
};

export const DEFAULT_FRAME_RATE = 30;
