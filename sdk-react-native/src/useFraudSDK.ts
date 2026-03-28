import { useFraudSDKContext } from './FraudSDKProvider';
import type { CollectionResult, SDKStatus, SendResult } from './types';

export interface UseFraudSDKReturn {
  collect: () => Promise<CollectionResult>;
  send: () => Promise<SendResult>;
  fingerprintId: string | null;
  status: SDKStatus;
  error: Error | null;
}

/**
 * Primary consumer hook for the Cosmos Fraud Detection SDK.
 *
 * Must be used inside a <FraudSDKProvider>. Throws if called outside one.
 *
 * State machine:
 *   idle → collecting → collected → sending → sent
 *                    ↘                      ↗
 *                      error ←←←←←←←←←←←←
 *
 * Usage:
 *   const { collect, send, fingerprintId, status, error } = useFraudSDK();
 *
 *   // On a sensitive action (e.g. checkout button press):
 *   const result = await collect();
 *   await send();
 */
export function useFraudSDK(): UseFraudSDKReturn {
  const { collect, send, fingerprintId, status, error } = useFraudSDKContext();

  return {
    collect,
    send,
    fingerprintId,
    status,
    error,
  };
}
