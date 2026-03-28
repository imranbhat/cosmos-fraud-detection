import { CollectionResult, SendResult } from './types';

const MAX_RETRIES = 3;
const BASE_BACKOFF_MS = 500;
const REQUEST_TIMEOUT_MS = 5000;

export interface TransportOptions {
  endpoint: string;
  clientId: string;
}

/**
 * Sends a collected fraud-detection payload to the configured endpoint.
 *
 * Retry policy:
 *  - Up to MAX_RETRIES (3) total attempts.
 *  - Exponential backoff: attempt n waits BASE_BACKOFF_MS * 2^(n-1) ms before retrying.
 *  - Network errors and 5xx responses are retried; 4xx are not.
 *  - Each attempt is abandoned after REQUEST_TIMEOUT_MS (5 s).
 */
export async function sendPayload(
  result: CollectionResult,
  options: TransportOptions,
): Promise<SendResult> {
  let lastError: Error | null = null;

  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    if (attempt > 0) {
      await sleep(BASE_BACKOFF_MS * Math.pow(2, attempt - 1));
    }

    try {
      const response = await fetchWithTimeout(
        options.endpoint,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Client-Id': options.clientId,
            'X-Fingerprint-Id': result.fingerprintId,
          },
          body: JSON.stringify(result),
        },
        REQUEST_TIMEOUT_MS,
      );

      if (response.ok) {
        return { fingerprintId: result.fingerprintId, status: 'success' };
      }

      // 4xx errors are non-retryable.
      if (response.status >= 400 && response.status < 500) {
        return { fingerprintId: result.fingerprintId, status: 'error' };
      }

      // 5xx — retryable.
      lastError = new Error(`HTTP ${response.status}: ${response.statusText}`);
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
    }
  }

  // All retries exhausted.
  console.error(
    `[FraudSDK] Failed to send payload after ${MAX_RETRIES} attempts:`,
    lastError?.message,
  );
  return { fingerprintId: result.fingerprintId, status: 'error' };
}

/**
 * Wraps the global fetch with an AbortController-based timeout.
 */
async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  const timerId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, { ...init, signal: controller.signal });
    return response;
  } catch (err) {
    if (controller.signal.aborted) {
      throw new Error(`Request timed out after ${timeoutMs}ms`);
    }
    throw err;
  } finally {
    clearTimeout(timerId);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
