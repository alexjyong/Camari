import { useState, useEffect } from 'react';
import { useStreaming, useBattery, useCamera, useNetworkStatus, useToast } from './hooks';
import { CameraStream } from './services/CameraStreamService';
import {
  StartStreamingButton,
  StreamUrlDisplay,
  CameraSwitchButton,
  StreamingIndicator,
  BatteryWarning,
  StopStreamingButton,
  Toast,
  HelpScreen,
  ResolutionPicker,
} from './components';
import type { ResolutionPreset } from './types/capacitor-camera-stream';
import './App.css';

const RESOLUTION_KEY = 'camari_resolution';
const VALID_PRESETS: ResolutionPreset[] = ['480p', '720p', '1080p'];

function loadResolution(): ResolutionPreset {
  const saved = localStorage.getItem(RESOLUTION_KEY);
  return VALID_PRESETS.includes(saved as ResolutionPreset) ? (saved as ResolutionPreset) : '720p';
}

function App() {
  const [showHelp, setShowHelp] = useState(false);
  const [cameraPermDenied, setCameraPermDenied] = useState(false);

  useEffect(() => {
    if (!cameraPermDenied) return;
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        setCameraPermDenied(false);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [cameraPermDenied]);
  const [resolution, setResolution] = useState<ResolutionPreset>(loadResolution);
  const { toast, showToast, dismissToast } = useToast();

  const handleResolutionChange = (preset: ResolutionPreset) => {
    setResolution(preset);
    localStorage.setItem(RESOLUTION_KEY, preset);
  };

  const {
    session,
    isStreaming,
    isObsConnected,
    isStarting,
    isStopping,
    startStreaming,
    stopStreaming,
  } = useStreaming({
    resolution,
    onStreamingStart: () => {},
    onStreamingStop: () => {
      showToast('Stream stopped', 'info');
    },
    onError: (error) => {
      if (error.message === 'CAMERA_PERMISSION_PERMANENTLY_DENIED') {
        setCameraPermDenied(true);
      } else {
        showToast(error.message || 'Failed to start streaming', 'error');
      }
    },
    onFallbackResolution: (actual: string) => {
      showToast(`Streaming at ${actual} — your device doesn't support the selected resolution`, 'info');
    },
  });

  const { cameraType, switchCamera, isSwitching } = useCamera({
    onCameraSwitch: (type) => {
      console.log('Switched to', type, 'camera');
    },
  });

  const { status: networkStatus } = useNetworkStatus();

  const { level: batteryLevel, isCharging } = useBattery({
    onLowBattery: (level) => {
      console.log('Low battery:', level, '%');
    },
  });

  return (
    <div className="app">
      <header className="app-header">
        <h1>Camari</h1>
        <button className="help-button" onClick={() => setShowHelp(true)} aria-label="Help">?</button>
      </header>

      {networkStatus.connectionType === 'none' && !networkStatus.ipAddress && (
        <div className="network-warning-banner">
          No local network detected — connect to WiFi or enable your phone's hotspot so OBS can reach your phone.
        </div>
      )}
      {networkStatus.connectionType === 'hotspot' && !isStreaming && (
        <div className="network-info-banner">
          Hotspot active — connect your OBS computer to your phone's hotspot to use this app.
        </div>
      )}

      {cameraPermDenied && (
        <div className="permission-denied-banner">
          Camera access was denied. Enable it in Settings to use Camari.
          <button
            className="permission-settings-button"
            onClick={() => CameraStream.openAppSettings()}
          >
            Open Settings
          </button>
        </div>
      )}

      <main className="app-main">
        {!isStreaming ? (
          <div className="start-screen">
            <ResolutionPicker
              value={resolution}
              onChange={handleResolutionChange}
              disabled={isStarting}
            />
            <StartStreamingButton
              onStart={startStreaming}
              isStarting={isStarting}
            />
            {!isStarting && <p className="hint">Tap to start streaming to OBS</p>}
          </div>
        ) : (
          <>
            <div className="streaming-screen">
              <StreamingIndicator
                startedAt={session?.startedAt || null}
                isStreaming={isStreaming}
              />

              <BatteryWarning
                batteryLevel={batteryLevel}
                isCharging={isCharging}
              />

              {!isObsConnected && (
                <div className="obs-disconnected-banner">
                  Waiting for OBS to connect — paste the URL below into an OBS Browser Source
                </div>
              )}

              <StreamUrlDisplay
                url={session?.streamUrl || ''}
                connectionType={networkStatus.connectionType}
                networkSsid={networkStatus.ssid}
              />

              <CameraSwitchButton
                onSwitch={switchCamera}
                currentCamera={cameraType}
                isSwitching={isSwitching}
              />
            </div>

            <div className="stop-button-tray">
              <StopStreamingButton
                onStop={stopStreaming}
                isStopping={isStopping}
                requireConfirmation={true}
              />
            </div>
          </>
        )}
      </main>

      <Toast toast={toast} onDismiss={dismissToast} />
      {showHelp && <HelpScreen onClose={() => setShowHelp(false)} />}
    </div>
  );
}

export default App;
