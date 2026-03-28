import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { collectDeviceInfo } from './collectors/device';
import { collectNetworkInfo } from './collectors/network';
import { collectLocation } from './collectors/location';
import { BehavioralCollector } from './collectors/behavioral';
import { sendPayload } from './transport';
import type { CollectionResult, FraudSDKConfig, SDKStatus, SendResult } from './types';

// ---------------------------------------------------------------------------
// Context shape
// ---------------------------------------------------------------------------

export interface FraudSDKContextValue {
  config: FraudSDKConfig;
  fingerprintId: string | null;
  status: SDKStatus;
  error: Error | null;
  behavioralCollector: BehavioralCollector;
  collect: () => Promise<CollectionResult>;
  send: () => Promise<SendResult>;
}

const FraudSDKContext = createContext<FraudSDKContextValue | null>(null);

// ---------------------------------------------------------------------------
// Provider props
// ---------------------------------------------------------------------------

export interface FraudSDKProviderProps {
  clientId: string;
  endpoint: string;
  /** Session timeout in milliseconds. Defaults to 30 minutes. */
  sessionTimeout?: number;
  children: React.ReactNode;
}

// ---------------------------------------------------------------------------
// Provider component
// ---------------------------------------------------------------------------

export function FraudSDKProvider({
  clientId,
  endpoint,
  sessionTimeout = 30 * 60 * 1000,
  children,
}: FraudSDKProviderProps): React.JSX.Element {
  const [fingerprintId, setFingerprintId] = useState<string | null>(null);
  const [status, setStatus] = useState<SDKStatus>('idle');
  const [error, setError] = useState<Error | null>(null);
  const [collectionResult, setCollectionResult] =
    useState<CollectionResult | null>(null);

  const behavioralCollector = useRef(new BehavioralCollector());
  const sessionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const config: FraudSDKConfig = { clientId, endpoint, sessionTimeout };

  // Start the behavioral collector on mount; stop it on unmount.
  useEffect(() => {
    behavioralCollector.current.start();

    return () => {
      behavioralCollector.current.stop();
      if (sessionTimerRef.current !== null) {
        clearTimeout(sessionTimerRef.current);
      }
    };
  }, []);

  // Reset status to idle when the session timeout expires.
  const resetSession = useCallback(() => {
    setFingerprintId(null);
    setCollectionResult(null);
    setStatus('idle');
    setError(null);
    behavioralCollector.current.reset();
    behavioralCollector.current.start();
  }, []);

  const scheduleSessionReset = useCallback(() => {
    if (sessionTimerRef.current !== null) {
      clearTimeout(sessionTimerRef.current);
    }
    sessionTimerRef.current = setTimeout(resetSession, sessionTimeout);
  }, [resetSession, sessionTimeout]);

  // ------------------------------------------------------------------
  // collect()
  // ------------------------------------------------------------------
  const collect = useCallback(async (): Promise<CollectionResult> => {
    setStatus('collecting');
    setError(null);

    try {
      const [deviceSignals, networkSignals, locationSignals] =
        await Promise.all([
          Promise.resolve(collectDeviceInfo()),
          Promise.resolve(collectNetworkInfo()),
          collectLocation(),
        ]);

      const behavioralSignals = behavioralCollector.current.getSignals();

      const signals: Record<string, string> = {
        ...deviceSignals,
        ...networkSignals,
        ...(locationSignals ?? {}),
        ...behavioralSignals,
      };

      const newFingerprintId = generateFingerprintId(clientId, signals);

      const result: CollectionResult = {
        fingerprintId: newFingerprintId,
        signals,
        timestamp: Date.now(),
      };

      setFingerprintId(newFingerprintId);
      setCollectionResult(result);
      setStatus('collected');
      scheduleSessionReset();

      return result;
    } catch (err) {
      const e = err instanceof Error ? err : new Error(String(err));
      setError(e);
      setStatus('error');
      throw e;
    }
  }, [clientId, scheduleSessionReset]);

  // ------------------------------------------------------------------
  // send()
  // ------------------------------------------------------------------
  const send = useCallback(async (): Promise<SendResult> => {
    if (!collectionResult) {
      const e = new Error(
        'No collected data available. Call collect() before send().',
      );
      setError(e);
      setStatus('error');
      throw e;
    }

    setStatus('sending');
    setError(null);

    try {
      const result = await sendPayload(collectionResult, { endpoint, clientId });

      if (result.status === 'success') {
        setStatus('sent');
      } else {
        const e = new Error('Transport returned error status.');
        setError(e);
        setStatus('error');
      }

      return result;
    } catch (err) {
      const e = err instanceof Error ? err : new Error(String(err));
      setError(e);
      setStatus('error');
      throw e;
    }
  }, [collectionResult, clientId, endpoint]);

  const value: FraudSDKContextValue = {
    config,
    fingerprintId,
    status,
    error,
    behavioralCollector: behavioralCollector.current,
    collect,
    send,
  };

  return (
    <FraudSDKContext.Provider value={value}>
      {children}
    </FraudSDKContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Internal hook (used by useFraudSDK)
// ---------------------------------------------------------------------------

export function useFraudSDKContext(): FraudSDKContextValue {
  const ctx = useContext(FraudSDKContext);
  if (ctx === null) {
    throw new Error(
      'useFraudSDK must be used inside a <FraudSDKProvider>.',
    );
  }
  return ctx;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function generateFingerprintId(
  clientId: string,
  signals: Record<string, string>,
): string {
  const deviceId = signals['device.uniqueId'] ?? 'unknown';
  const ts = Date.now().toString(36);
  const rand = Math.random().toString(36).slice(2, 7);
  // Simple deterministic prefix keeps fingerprints human-readable in logs.
  return `fp_${clientId.slice(0, 4)}_${deviceId.slice(-6)}_${ts}_${rand}`;
}
