export interface FraudSDKConfig {
  clientId: string;
  endpoint: string;
  sessionTimeout?: number; // ms, default 30min
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

export type SDKEvent = 'error' | 'collected' | 'sent';
export type EventHandler = (data: unknown) => void;

export interface BehavioralSignals {
  mouseMovements: Array<{ x: number; y: number; t: number }>;
  keystrokeIntervals: number[];
  touchEvents: Array<{ x: number; y: number; t: number; force?: number }>;
}
