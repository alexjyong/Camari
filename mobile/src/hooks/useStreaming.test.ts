import { renderHook, act } from '@testing-library/react';
import { useStreaming } from './useStreaming';
import { CameraStream } from '../services/CameraStreamService';

jest.mock('../services/CameraStreamService');

const mockCameraStream = CameraStream as jest.Mocked<typeof CameraStream>;

const STREAMING_RESULT = {
  streamUrl: 'http://192.168.1.100:8080/',
  ipAddress: '192.168.1.100',
  port: 8080,
  networkSsid: 'TestNet',
  cameraType: 'front' as const,
};

describe('useStreaming', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockCameraStream.getStatus.mockResolvedValue({
      status: 'streaming',
      cameraType: 'front',
      batteryLevel: 80,
      isCharging: false,
      isLowBattery: false,
      connectionType: 'wifi',
      networkSsid: 'TestNet',
      ipAddress: '192.168.1.100',
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('starts in idle state with no session', () => {
    const { result } = renderHook(() => useStreaming());
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.isStarting).toBe(false);
    expect(result.current.session).toBeNull();
  });

  it('sets isStarting while startStreaming is in flight', async () => {
    // Don't resolve yet — check intermediate state
    let resolveStart!: (v: typeof STREAMING_RESULT) => void;
    mockCameraStream.startStreaming.mockReturnValue(
      new Promise(res => { resolveStart = res; })
    );

    const { result } = renderHook(() => useStreaming());

    act(() => { result.current.startStreaming(); });
    expect(result.current.isStarting).toBe(true);

    await act(async () => { resolveStart(STREAMING_RESULT); });
    expect(result.current.isStarting).toBe(false);
  });

  it('transitions to streaming with correct session data on success', async () => {
    mockCameraStream.startStreaming.mockResolvedValue(STREAMING_RESULT);

    const { result } = renderHook(() => useStreaming());

    await act(async () => { await result.current.startStreaming(); });

    expect(result.current.isStreaming).toBe(true);
    expect(result.current.session?.status).toBe('streaming');
    expect(result.current.session?.streamUrl).toBe('http://192.168.1.100:8080/');
    expect(result.current.session?.ipAddress).toBe('192.168.1.100');
    expect(result.current.session?.port).toBe(8080);
  });

  it('sets error state when startStreaming rejects', async () => {
    mockCameraStream.startStreaming.mockRejectedValue(new Error('Camera permission denied'));

    const onError = jest.fn();
    const { result } = renderHook(() => useStreaming({ onError }));

    await act(async () => { await result.current.startStreaming(); });

    expect(result.current.isStreaming).toBe(false);
    expect(result.current.session?.status).toBe('error');
    expect(result.current.session?.errorMessage).toBe('Camera permission denied');
    expect(onError).toHaveBeenCalledWith(expect.any(Error));
  });

  it('clears error and resets to idle on clearError', async () => {
    mockCameraStream.startStreaming.mockRejectedValue(new Error('Failed'));

    const { result } = renderHook(() => useStreaming());
    await act(async () => { await result.current.startStreaming(); });
    expect(result.current.session?.status).toBe('error');

    act(() => { result.current.clearError(); });
    expect(result.current.session?.status).toBe('idle');
    expect(result.current.session?.errorMessage).toBeNull();
  });

  it('stops streaming and calls stopStreaming on plugin', async () => {
    mockCameraStream.startStreaming.mockResolvedValue(STREAMING_RESULT);
    mockCameraStream.stopStreaming.mockResolvedValue(undefined);

    const onStop = jest.fn();
    const { result } = renderHook(() => useStreaming({ onStreamingStop: onStop }));

    await act(async () => { await result.current.startStreaming(); });
    expect(result.current.isStreaming).toBe(true);

    await act(async () => { await result.current.stopStreaming(); });
    expect(mockCameraStream.stopStreaming).toHaveBeenCalledTimes(1);
    expect(result.current.session?.status).toBe('stopped');

    // After the 500ms reset delay
    await act(async () => { jest.advanceTimersByTime(600); });
    expect(result.current.session).toBeNull();
    expect(onStop).toHaveBeenCalled();
  });

  it('calls onStreamingStart callback with session data', async () => {
    mockCameraStream.startStreaming.mockResolvedValue(STREAMING_RESULT);

    const onStart = jest.fn();
    const { result } = renderHook(() => useStreaming({ onStreamingStart: onStart }));

    await act(async () => { await result.current.startStreaming(); });

    expect(onStart).toHaveBeenCalledWith(
      expect.objectContaining({ streamUrl: STREAMING_RESULT.streamUrl })
    );
  });

  it('does not call startStreaming twice if already starting', async () => {
    let resolve!: (v: typeof STREAMING_RESULT) => void;
    mockCameraStream.startStreaming.mockReturnValue(new Promise(r => { resolve = r; }));

    const { result } = renderHook(() => useStreaming());

    act(() => { result.current.startStreaming(); });
    act(() => { result.current.startStreaming(); }); // second call while first is in flight

    await act(async () => { resolve(STREAMING_RESULT); });

    expect(mockCameraStream.startStreaming).toHaveBeenCalledTimes(1);
  });
});
