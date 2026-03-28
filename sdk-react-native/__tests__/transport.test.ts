import { sendPayload } from '../src/transport';
import type { CollectionResult } from '../src/types';

// ---------------------------------------------------------------------------
// Fixture
// ---------------------------------------------------------------------------

const mockResult: CollectionResult = {
  fingerprintId: 'fp_test_abc123_xyz_rand',
  signals: {
    'device.model': 'Test Model',
    'device.brand': 'Test Brand',
  },
  timestamp: 1_700_000_000_000,
};

const mockOptions = {
  endpoint: 'https://api.cosmos.example/v1/fingerprint',
  clientId: 'test-client-id',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockFetchResponse(status: number, ok: boolean): jest.Mock {
  const mock = jest.fn().mockResolvedValue({
    ok,
    status,
    statusText: ok ? 'OK' : 'Internal Server Error',
  });
  global.fetch = mock;
  return mock;
}

function mockFetchFailure(error: Error): jest.Mock {
  const mock = jest.fn().mockRejectedValue(error);
  global.fetch = mock;
  return mock;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('sendPayload', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('returns success on a 200 response', async () => {
    mockFetchResponse(200, true);

    const promise = sendPayload(mockResult, mockOptions);
    // No timers needed — resolves immediately on first attempt.
    const result = await promise;

    expect(result.status).toBe('success');
    expect(result.fingerprintId).toBe(mockResult.fingerprintId);
  });

  it('sends a POST with correct headers', async () => {
    const fetchMock = mockFetchResponse(200, true);

    const promise = sendPayload(mockResult, mockOptions);
    await promise;

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(mockOptions.endpoint);
    expect(init.method).toBe('POST');
    const headers = init.headers as Record<string, string>;
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers['X-Client-Id']).toBe(mockOptions.clientId);
    expect(headers['X-Fingerprint-Id']).toBe(mockResult.fingerprintId);
  });

  it('sends the payload body as JSON', async () => {
    const fetchMock = mockFetchResponse(200, true);

    const promise = sendPayload(mockResult, mockOptions);
    await promise;

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual(mockResult);
  });

  it('returns error immediately on a 4xx response (non-retryable)', async () => {
    const fetchMock = mockFetchResponse(400, false);
    fetchMock.mockResolvedValue({ ok: false, status: 400, statusText: 'Bad Request' });

    const promise = sendPayload(mockResult, mockOptions);
    const result = await promise;

    expect(result.status).toBe('error');
    // Should not retry on 4xx — only called once.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('retries on 5xx and returns error after 3 attempts', async () => {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
    });
    global.fetch = fetchMock;

    const promise = sendPayload(mockResult, mockOptions);

    // Drain all backoff timers: attempt 0 fires immediately,
    // attempt 1 waits 500ms, attempt 2 waits 1000ms.
    await jest.runAllTimersAsync();

    const result = await promise;

    expect(result.status).toBe('error');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('retries on network error and succeeds on third attempt', async () => {
    const networkError = new Error('Network request failed');
    const fetchMock = jest
      .fn()
      .mockRejectedValueOnce(networkError)
      .mockRejectedValueOnce(networkError)
      .mockResolvedValue({ ok: true, status: 200, statusText: 'OK' });
    global.fetch = fetchMock;

    const promise = sendPayload(mockResult, mockOptions);
    await jest.runAllTimersAsync();
    const result = await promise;

    expect(result.status).toBe('success');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('returns error when fetch times out', async () => {
    // Simulate a fetch that never resolves so the AbortController fires.
    global.fetch = jest.fn().mockImplementation(() => new Promise(() => {}));

    const promise = sendPayload(mockResult, mockOptions);
    // Advance past the 5 s timeout on every attempt and through backoff.
    await jest.runAllTimersAsync();
    const result = await promise;

    expect(result.status).toBe('error');
  });
});
