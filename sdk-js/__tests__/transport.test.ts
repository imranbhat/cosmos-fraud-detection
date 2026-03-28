/**
 * Tests for transport layer: fetch, retry, beacon fallback.
 */

import { sendSignals } from '../src/transport';
import { CollectionResult } from '../src/types';

const ENDPOINT = 'https://api.cosmos.example/v1/signals';
const CLIENT_ID = 'test-client-001';

const mockResult: CollectionResult = {
  fingerprintId: 'abc123def456',
  signals: {
    'browser.userAgent': 'TestBrowser/1.0',
    'screen.width': '1920',
  },
  timestamp: 1700000000000,
};

// Helper to advance timers for retry backoff
function flushTimers() {
  return jest.runAllTimersAsync !== undefined
    ? jest.runAllTimersAsync()
    : Promise.resolve(jest.runAllTimers());
}

describe('transport.sendSignals', () => {
  let originalFetch: typeof globalThis.fetch;
  let originalSendBeacon: Navigator['sendBeacon'] | undefined;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
    originalSendBeacon = navigator.sendBeacon;
    jest.useFakeTimers();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    // @ts-expect-error - restore
    navigator.sendBeacon = originalSendBeacon;
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('returns success when fetch responds with 200', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
    });

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    const result = await promise;

    expect(result.status).toBe('success');
    expect(result.fingerprintId).toBe(mockResult.fingerprintId);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it('sends correct payload and headers', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200 });

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    await promise;

    const [url, options] = (globalThis.fetch as jest.Mock).mock.calls[0] as [string, RequestInit];
    expect(url).toBe(ENDPOINT);
    expect(options.method).toBe('POST');

    const headers = options.headers as Record<string, string>;
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers['X-Client-Id']).toBe(CLIENT_ID);
    expect(headers['X-SDK-Version']).toBe('1.0.0');

    const body = JSON.parse(options.body as string) as Record<string, unknown>;
    expect(body.fingerprintId).toBe(mockResult.fingerprintId);
    expect(body.clientId).toBe(CLIENT_ID);
  });

  it('retries on fetch failure and succeeds on third attempt', async () => {
    let callCount = 0;
    globalThis.fetch = jest.fn().mockImplementation(() => {
      callCount++;
      if (callCount < 3) {
        return Promise.reject(new Error('network error'));
      }
      return Promise.resolve({ ok: true, status: 200 });
    });

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    // Advance timers to flush backoff delays (100ms, 400ms)
    await flushTimers();
    const result = await promise;

    expect(result.status).toBe('success');
    expect(globalThis.fetch).toHaveBeenCalledTimes(3);
  });

  it('returns error and falls back to beacon when all retries fail', async () => {
    globalThis.fetch = jest.fn().mockRejectedValue(new Error('network error'));

    const beaconMock = jest.fn().mockReturnValue(false);
    // @ts-expect-error - mock
    navigator.sendBeacon = beaconMock;

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    const result = await promise;

    expect(globalThis.fetch).toHaveBeenCalledTimes(3);
    expect(beaconMock).toHaveBeenCalledTimes(1);
    expect(result.status).toBe('error');
    expect(result.fingerprintId).toBe(mockResult.fingerprintId);
  });

  it('falls back to beacon and returns success when fetch fails but beacon succeeds', async () => {
    globalThis.fetch = jest.fn().mockRejectedValue(new Error('network error'));

    const beaconMock = jest.fn().mockReturnValue(true);
    // @ts-expect-error - mock
    navigator.sendBeacon = beaconMock;

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    const result = await promise;

    expect(beaconMock).toHaveBeenCalledTimes(1);
    expect(result.status).toBe('success');
  });

  it('uses beacon with JSON blob', async () => {
    globalThis.fetch = jest.fn().mockRejectedValue(new Error('fail'));
    const beaconMock = jest.fn().mockReturnValue(true);
    // @ts-expect-error - mock
    navigator.sendBeacon = beaconMock;

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    await promise;

    const [url, blob] = beaconMock.mock.calls[0] as [string, Blob];
    expect(url).toBe(ENDPOINT);
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe('application/json');
  });

  it('includes behavioral data in the payload when provided', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200 });

    const behavioral = {
      mouseMovements: [{ x: 1, y: 2, t: 100 }],
      keystrokeIntervals: [50],
      touchEvents: [],
    };

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult, behavioral);
    await flushTimers();
    await promise;

    const [, options] = (globalThis.fetch as jest.Mock).mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(options.body as string) as Record<string, unknown>;
    expect(body).toHaveProperty('behavioral');
    expect(body.behavioral).toEqual(behavioral);
  });

  it('returns error status when fetch returns non-ok response on all retries', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: false, status: 500 });
    const beaconMock = jest.fn().mockReturnValue(false);
    // @ts-expect-error - mock
    navigator.sendBeacon = beaconMock;

    const promise = sendSignals(ENDPOINT, CLIENT_ID, mockResult);
    await flushTimers();
    const result = await promise;

    expect(globalThis.fetch).toHaveBeenCalledTimes(3);
    expect(result.status).toBe('error');
  });
});
