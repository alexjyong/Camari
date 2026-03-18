/**
 * Camera types and enums.
 */

export type CameraType = 'front' | 'back';

export enum CameraTypeEnum {
  FRONT = 'front',
  BACK = 'back',
}

export interface CameraInfo {
  /** Android camera ID */
  cameraId: string;
  /** Front or back camera */
  lensFacing: CameraType;
  /** Whether camera is available (not in use) */
  isAvailable: boolean;
}

export const DEFAULT_CAMERA_TYPE: CameraType = 'front';
