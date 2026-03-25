import { useState } from 'react';
import { useStreaming, useBattery, useCamera, useNetworkStatus } from './hooks';
import {
  StartStreamingButton,
  StreamUrlDisplay,
  CameraSwitchButton,
  StreamingIndicator,
  BatteryWarning,
  StopStreamingButton,
} from './components';
import './App.css';

function App() {
  const [showError, setShowError] = useState(false);

  const {
    session,
    isStreaming,
    isObsConnected,
    isStarting,
    isStopping,
    startStreaming,
    stopStreaming,
    clearError,
  } = useStreaming({
    onStreamingStart: () => {
      console.log('Streaming started');
      setShowError(false);
    },
    onStreamingStop: () => {
      console.log('Streaming stopped');
    },
    onError: (error) => {
      console.error('Streaming error:', error);
      setShowError(true);
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

  const handleStartError = () => {
    clearError();
    setShowError(false);
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Camari</h1>
      </header>

      {networkStatus.connectionType === 'none' && (
        <div className="network-warning-banner">
          ⚠️ No local network detected. Connect to WiFi or enable your phone's hotspot; OBS needs a direct connection to your phone.
        </div>
      )}

      <main className="app-main">
        {!isStreaming ? (
          <div className="start-screen">
            <StartStreamingButton
              onStart={startStreaming}
              isStarting={isStarting}
            />
            <p className="hint">Tap to start streaming to OBS</p>
            
            {showError && session?.errorMessage && (
              <div className="error-banner">
                <p className="error-title">⚠️ Streaming Error</p>
                <p className="error-message">{session.errorMessage}</p>
                <button className="retry-button" onClick={handleStartError}>
                  Try Again
                </button>
              </div>
            )}
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
                  ⏳ Waiting for OBS to connect — paste the URL below into an OBS Browser Source
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

    </div>
  );
}

export default App;
