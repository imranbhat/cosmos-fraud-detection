import { CollectionResult, SendResult } from './types';

const MAX_RETRIES = 3;
const BASE_BACKOFF_MS = 100;
const REQUEST_TIMEOUT_MS = 5000;

interface TransportPayload {
  clientId: string;
  fingerprintId: string;
  signals: Record<string, string>;
  timestamp: number;
  behavioral?: unknown;
}

export async function sendSignals(
  endpoint: string,
  clientId: string,
  result: CollectionResult,
  behavioral?: unknown
): Promise<SendResult> {
  const payload: TransportPayload = {
    clientId,
    fingerprintId: result.fingerprintId,
    signals: result.signals,
    timestamp: result.timestamp,
  };

  if (behavioral !== undefined) {
    payload.behavioral = behavioral;
  }

  const body = JSON.stringify(payload);

  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    if (attempt > 0) {
      // Exponential backoff: 100ms, 400ms, 1600ms
      await sleep(BASE_BACKOFF_MS * Math.pow(4, attempt - 1));
    }

    try {
      const success = await fetchWithTimeout(endpoint, clientId, body);
      if (success) {
        return { fingerprintId: result.fingerprintId, status: 'success' };
      }
    } catch {
      // Will retry or fall back to beacon
    }
  }

  // Fallback: navigator.sendBeacon
  const beaconSuccess = sendBeaconFallback(endpoint, body);
  if (beaconSuccess) {
    return { fingerprintId: result.fingerprintId, status: 'success' };
  }

  return { fingerprintId: result.fingerprintId, status: 'error' };
}

async function fetchWithTimeout(
  endpoint: string,
  clientId: string,
  body: string
): Promise<boolean> {
  const controller = new AbortController();
  const timerId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Id': clientId,
        'X-SDK-Version': '1.0.0',
      },
      body,
      signal: controller.signal,
      keepalive: true,
    });
    return response.ok;
  } finally {
    clearTimeout(timerId);
  }
}

function sendBeaconFallback(endpoint: string, body: string): boolean {
  if (typeof navigator === 'undefined' || typeof navigator.sendBeacon !== 'function') {
    return false;
  }
  try {
    const blob = new Blob([body], { type: 'application/json' });
    return navigator.sendBeacon(endpoint, blob);
  } catch {
    return false;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
