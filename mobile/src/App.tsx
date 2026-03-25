import { useState } from 'react';
import { useStreaming, useBattery, useCamera, useNetworkStatus, useToast } from './hooks';
import {
  StartStreamingButton,
  StreamUrlDisplay,
  CameraSwitchButton,
  StreamingIndicator,
  BatteryWarning,
  StopStreamingButton,
  Toast,
  HelpScreen,
} from './components';
import './App.css';

function App() {
  const [showHelp, setShowHelp] = useState(false);
  const { toast, showToast, dismissToast } = useToast();

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
    onStreamingStart: () => {},
    onStreamingStop: () => {
      showToast('Stream stopped', 'info');
    },
    onError: (error) => {
      showToast(error.message || 'Failed to start streaming', 'error');
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

      <Toast toast={toast} onDismiss={dismissToast} />
      {showHelp && <HelpScreen onClose={() => setShowHelp(false)} />}
    </div>
  );
}

export default App;
