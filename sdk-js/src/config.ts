import { FraudSDKConfig } from './types';

const DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes in ms

let _config: FraudSDKConfig | null = null;

export function validateConfig(config: FraudSDKConfig): void {
  if (!config.clientId || typeof config.clientId !== 'string' || config.clientId.trim() === '') {
    throw new Error('[FraudSDK] config.clientId is required and must be a non-empty string');
  }
  if (!config.endpoint || typeof config.endpoint !== 'string' || config.endpoint.trim() === '') {
    throw new Error('[FraudSDK] config.endpoint is required and must be a non-empty string');
  }
  try {
    new URL(config.endpoint);
  } catch {
    throw new Error(`[FraudSDK] config.endpoint "${config.endpoint}" is not a valid URL`);
  }
  if (
    config.sessionTimeout !== undefined &&
    (typeof config.sessionTimeout !== 'number' || config.sessionTimeout <= 0)
  ) {
    throw new Error('[FraudSDK] config.sessionTimeout must be a positive number (ms)');
  }
}

export function storeConfig(config: FraudSDKConfig): void {
  validateConfig(config);
  _config = {
    ...config,
    sessionTimeout: config.sessionTimeout ?? DEFAULT_SESSION_TIMEOUT,
  };
}

export function getConfig(): FraudSDKConfig {
  if (!_config) {
    throw new Error('[FraudSDK] SDK not initialized. Call FraudSDK.init(config) first.');
  }
  return _config;
}

export function clearConfig(): void {
  _config = null;
}

export function isInitialized(): boolean {
  return _config !== null;
}
