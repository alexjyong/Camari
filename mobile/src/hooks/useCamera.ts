import { useState, useEffect, useCallback } from 'react';
import { CameraStream } from '@capacitor/camera-stream';
import { CameraType, DEFAULT_CAMERA_TYPE } from '../types/camera';

interface UseCameraOptions {
  /** Initial camera type */
  initialCameraType?: CameraType;
  /** Callback when camera switches */
  onCameraSwitch?: (type: CameraType) => void;
  /** Callback when camera error occurs */
  onError?: (error: Error) => void;
}

interface UseCameraReturn {
  /** Current camera type */
  cameraType: CameraType;
  /** Whether camera is currently active */
  isActive: boolean;
  /** Whether camera is switching */
  isSwitching: boolean;
  /** Switch between front and back cameras */
  switchCamera: () => Promise<void>;
  /** Reset camera state */
  reset: () => void;
}

/**
 * Hook for managing camera state and switching.
 */
export function useCamera(options: UseCameraOptions = {}): UseCameraReturn {
  const {
    initialCameraType = DEFAULT_CAMERA_TYPE,
    onCameraSwitch,
    onError,
  } = options;

  const [cameraType, setCameraType] = useState<CameraType>(initialCameraType);
  const [isActive, setIsActive] = useState(false);
  const [isSwitching, setIsSwitching] = useState(false);

  /**
   * Switch between front and back cameras.
   */
  const switchCamera = useCallback(async () => {
    if (isSwitching) return;

    setIsSwitching(true);

    try {
      const result = await CameraStream.switchCamera();
      
      if (result.success) {
        const newType = result.cameraType as CameraType;
        setCameraType(newType);
        setIsActive(true);
        onCameraSwitch?.(newType);
      } else {
        console.warn('Camera switch failed');
      }
    } catch (error) {
      console.error('Error switching camera:', error);
      onError?.(error as Error);
    } finally {
      setIsSwitching(false);
    }
  }, [isSwitching, onCameraSwitch, onError]);

  /**
   * Reset camera state.
   */
  const reset = useCallback(() => {
    setCameraType(initialCameraType);
    setIsActive(false);
    setIsSwitching(false);
  }, [initialCameraType]);

  return {
    cameraType,
    isActive,
    isSwitching,
    switchCamera,
    reset,
  };
}
