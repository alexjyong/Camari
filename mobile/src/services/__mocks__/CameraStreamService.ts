/**
 * Manual Jest mock for CameraStreamService.
 * Used automatically when tests call jest.mock('../services/CameraStreamService').
 *
 * Tests set up their own return values via mockResolvedValue / mockResolvedValueOnce.
 * Default implementations return sensible idle-state values so tests that don't care
 * about network status don't need to configure every call.
 */
import type { CameraStreamPlugin } from '../../types/capacitor-camera-stream';

export const CameraStream: jest.Mocked<CameraStreamPlugin> = {
  startStreaming: jest.fn().mockResolvedValue({
    streamUrl: 'http://192.168.1.100:8080/',
    ipAddress: '192.168.1.100',
    port: 8080,
    networkSsid: 'TestNet',
    cameraType: 'front' as const,
  }),
  stopStreaming: jest.fn().mockResolvedValue(undefined),
  switchCamera: jest.fn().mockResolvedValue({ cameraType: 'back' as const, success: true }),
  setOrientation: jest.fn().mockResolvedValue(undefined),
  getStatus: jest.fn().mockResolvedValue({
    status: 'idle' as const,
    cameraType: null,
    batteryLevel: 80,
    isCharging: false,
    isLowBattery: false,
    connectionType: 'wifi' as const,
    networkSsid: 'TestNet',
    ipAddress: '192.168.1.100',
    obsConnected: false,
  }),
  addListener: jest.fn().mockResolvedValue({ remove: jest.fn() }),
};
