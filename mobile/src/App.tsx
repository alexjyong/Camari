import { useState } from 'react';
import { useStreaming, useBattery, useCamera } from './hooks';
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
    isStarting, 
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

  const { batteryLevel, isCharging } = useBattery({
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
        <p className="tagline">Android Webcam for OBS</p>
      </header>

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
          <div className="streaming-screen">
            <StreamingIndicator 
              startedAt={session?.startedAt || null}
              isStreaming={isStreaming}
            />

            <BatteryWarning 
              batteryLevel={batteryLevel}
              isCharging={isCharging}
            />

            <StreamUrlDisplay 
              url={session?.streamUrl || ''}
              networkSsid={session?.networkSsid}
              ipAddress={session?.ipAddress}
            />

            <CameraSwitchButton 
              onSwitch={switchCamera}
              currentCamera={cameraType}
              isSwitching={isSwitching}
            />

            <StopStreamingButton 
              onStop={stopStreaming}
              requireConfirmation={true}
            />
          </div>
        )}
      </main>

      <footer className="app-footer">
        <p>📶 Make sure your phone and computer are on the same WiFi network</p>
      </footer>
    </div>
  );
}

export default App;
