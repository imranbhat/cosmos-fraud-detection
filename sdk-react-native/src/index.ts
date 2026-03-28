// Provider & context
export { FraudSDKProvider } from './FraudSDKProvider';
export type { FraudSDKProviderProps, FraudSDKContextValue } from './FraudSDKProvider';

// Hook
export { useFraudSDK } from './useFraudSDK';
export type { UseFraudSDKReturn } from './useFraudSDK';

// Types
export type {
  FraudSDKConfig,
  CollectionResult,
  SendResult,
  SDKStatus,
} from './types';

// Collectors (exported for advanced / custom integrations)
export { collectDeviceInfo } from './collectors/device';
export type { DeviceInfo } from './collectors/device';

export { collectNetworkInfo } from './collectors/network';
export type { NetworkInfo } from './collectors/network';

export { collectLocation } from './collectors/location';
export type { LocationInfo } from './collectors/location';

export { BehavioralCollector } from './collectors/behavioral';
export type { AccelerometerSample, BehavioralData } from './collectors/behavioral';

// Transport (exported for advanced / custom integrations)
export { sendPayload } from './transport';
export type { TransportOptions } from './transport';
