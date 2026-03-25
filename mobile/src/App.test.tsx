import { render, screen, fireEvent, act } from '@testing-library/react';
import App from './App';
import { CameraStream } from './services/CameraStreamService';

jest.mock('./services/CameraStreamService');

const mockCameraStream = CameraStream as jest.Mocked<typeof CameraStream>;

function idleStatus(overrides = {}) {
  return {
    status: 'idle' as const,
    cameraType: null,
    batteryLevel: 80,
    isCharging: false,
    isLowBattery: false,
    connectionType: 'wifi' as const,
    networkSsid: 'TestNet',
    ipAddress: '192.168.1.100',
    ...overrides,
  };
}

describe('App', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockCameraStream.getStatus.mockResolvedValue(idleStatus());
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('renders app header with Camari title', async () => {
    await act(async () => { render(<App />); });
    expect(screen.getByText('Camari')).toBeInTheDocument();
  });

  it('shows Start Streaming button when idle', async () => {
    await act(async () => { render(<App />); });
    expect(screen.getByRole('button', { name: /Start streaming to OBS/i })).toBeInTheDocument();
  });

  it('shows network warning banner when connectionType is none', async () => {
    mockCameraStream.getStatus.mockResolvedValue(
      idleStatus({ connectionType: 'none', networkSsid: null, ipAddress: null })
    );

    await act(async () => { render(<App />); });

    // Advance past the first poll interval so getStatus is called
    await act(async () => { jest.advanceTimersByTime(2500); });

    expect(screen.getByText(/No local network detected/)).toBeInTheDocument();
  });

  it('hides network warning banner when connected to WiFi', async () => {
    mockCameraStream.getStatus.mockResolvedValue(idleStatus({ connectionType: 'wifi' }));

    await act(async () => { render(<App />); });
    await act(async () => { jest.advanceTimersByTime(2500); });

    expect(screen.queryByText(/No local network detected/)).not.toBeInTheDocument();
  });

  it('shows streaming screen after startStreaming succeeds', async () => {
    mockCameraStream.startStreaming.mockResolvedValue({
      streamUrl: 'http://192.168.1.100:8080/',
      ipAddress: '192.168.1.100',
      port: 8080,
      networkSsid: 'TestNet',
      cameraType: 'front',
    });

    await act(async () => { render(<App />); });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Start streaming to OBS/i }));
    });

    expect(await screen.findByText('http://192.168.1.100:8080/')).toBeInTheDocument();
  });
});
