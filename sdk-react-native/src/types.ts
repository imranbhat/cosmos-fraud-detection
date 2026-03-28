export interface FraudSDKConfig {
  clientId: string;
  endpoint: string;
  sessionTimeout?: number;
}

export interface CollectionResult {
  fingerprintId: string;
  signals: Record<string, string>;
  timestamp: number;
}

export interface SendResult {
  fingerprintId: string;
  status: 'success' | 'error';
}

export type SDKStatus =
  | 'idle'
  | 'collecting'
  | 'collected'
  | 'sending'
  | 'sent'
  | 'error';
