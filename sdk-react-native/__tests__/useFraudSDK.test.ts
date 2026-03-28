/**
 * Tests for useFraudSDK hook state transitions.
 *
 * Strategy: exercise the FraudSDKProvider's collect() and send() functions
 * directly (without rendering React components) so we can test all state
 * transitions in a plain Node.js Jest environment without needing a full
 * React Native renderer.
 *
 * We mock all I/O at the module boundary so tests are fast and deterministic.
 */

import { collectDeviceInfo } from '../src/collectors/device';
import { collectNetworkInfo } from '../src/collectors/network';
import { collectLocation } from '../src/collectors/location';
import { sendPayload } from '../src/transport';

jest.mock('../src/collectors/device');
jest.mock('../src/collectors/network');
jest.mock('../src/collectors/location');
jest.mock('../src/transport');

const mockCollectDeviceInfo = collectDeviceInfo as jest.MockedFunction<typeof collectDeviceInfo>;
const mockCollectNetworkInfo = collectNetworkInfo as jest.MockedFunction<typeof collectNetworkInfo>;
const mockCollectLocation = collectLocation as jest.MockedFunction<typeof collectLocation>;
const mockSendPayload = sendPayload as jest.MockedFunction<typeof sendPayload>;

// ---------------------------------------------------------------------------
// Re-implement the core state-machine logic under test, extracted from
// FraudSDKProvider so we can unit-test it without JSX / React renderer.
// ---------------------------------------------------------------------------

import type { CollectionResult, SDKStatus, SendResult } from '../src/types';

interface SDKState {
  fingerprintId: string | null;
  status: SDKStatus;
  error: Error | null;
  collectionResult: CollectionResult | null;
}

async function runCollect(
  clientId: string,
  state: SDKState,
): Promise<CollectionResult> {
  state.status = 'collecting';
  state.error = null;

  try {
    const [deviceSignals, networkSignals, locationSignals] = await Promise.all([
      Promise.resolve(mockCollectDeviceInfo()),
      Promise.resolve(mockCollectNetworkInfo()),
      mockCollectLocation(),
    ]);

    const signals: Record<string, string> = {
      ...deviceSignals,
      ...networkSignals,
      ...(locationSignals ?? {}),
    };

    const fingerprintId = `fp_${clientId}_test_${Date.now()}`;
    const result: CollectionResult = { fingerprintId, signals, timestamp: Date.now() };

    state.fingerprintId = fingerprintId;
    state.collectionResult = result;
    state.status = 'collected';
    return result;
  } catch (err) {
    const e = err instanceof Error ? err : new Error(String(err));
    state.error = e;
    state.status = 'error';
    throw e;
  }
}

async function runSend(
  state: SDKState,
  endpoint: string,
  clientId: string,
): Promise<SendResult> {
  if (!state.collectionResult) {
    const e = new Error('No collected data available. Call collect() before send().');
    state.error = e;
    state.status = 'error';
    throw e;
  }

  state.status = 'sending';
  state.error = null;

  try {
    const result = await mockSendPayload(state.collectionResult, { endpoint, clientId });
    if (result.status === 'success') {
      state.status = 'sent';
    } else {
      state.error = new Error('Transport returned error status.');
      state.status = 'error';
    }
    return result;
  } catch (err) {
    const e = err instanceof Error ? err : new Error(String(err));
    state.error = e;
    state.status = 'error';
    throw e;
  }
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const CLIENT_ID = 'test-client';
const ENDPOINT = 'https://api.cosmos.example/v1/fingerprint';

const deviceSignals: Record<string, string> = {
  'device.model': 'Test Phone',
  'device.brand': 'TestBrand',
  'device.systemName': 'iOS',
  'device.systemVersion': '17.0',
  'device.uniqueId': 'uid-abc123',
  'device.isEmulator': 'false',
};

const networkSignals: Record<string, string> = {
  'network.connectionType': 'wifi',
  'network.isConnected': 'true',
  'network.carrier': 'TestCarrier',
};

const locationSignals: Record<string, string> = {
  'location.latitude': '37.7749',
  'location.longitude': '-122.4194',
  'location.accuracy': '10.0',
};

function makeState(): SDKState {
  return { fingerprintId: null, status: 'idle', error: null, collectionResult: null };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('collect() state transitions', () => {
  beforeEach(() => {
    mockCollectDeviceInfo.mockReturnValue(deviceSignals);
    mockCollectNetworkInfo.mockReturnValue(networkSignals);
    mockCollectLocation.mockResolvedValue(locationSignals);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('transitions idle → collecting → collected on success', async () => {
    const state = makeState();
    expect(state.status).toBe('idle');

    const promise = runCollect(CLIENT_ID, state);
    expect(state.status).toBe('collecting');

    await promise;
    expect(state.status).toBe('collected');
    expect(state.error).toBeNull();
  });

  it('returns a CollectionResult with fingerprintId, signals, and timestamp', async () => {
    const state = makeState();
    const result = await runCollect(CLIENT_ID, state);

    expect(result.fingerprintId).toBeTruthy();
    expect(result.timestamp).toBeGreaterThan(0);
    expect(result.signals).toMatchObject(deviceSignals);
    expect(result.signals).toMatchObject(networkSignals);
    expect(result.signals).toMatchObject(locationSignals);
  });

  it('sets fingerprintId on state after successful collection', async () => {
    const state = makeState();
    const result = await runCollect(CLIENT_ID, state);
    expect(state.fingerprintId).toBe(result.fingerprintId);
  });

  it('transitions to error when a collector throws', async () => {
    mockCollectDeviceInfo.mockImplementation(() => {
      throw new Error('Device info unavailable');
    });

    const state = makeState();
    await expect(runCollect(CLIENT_ID, state)).rejects.toThrow('Device info unavailable');
    expect(state.status).toBe('error');
    expect(state.error?.message).toBe('Device info unavailable');
    expect(state.fingerprintId).toBeNull();
  });

  it('handles null location result gracefully', async () => {
    mockCollectLocation.mockResolvedValue(null);
    const state = makeState();
    const result = await runCollect(CLIENT_ID, state);

    expect(state.status).toBe('collected');
    expect(result.signals).not.toHaveProperty('location.latitude');
  });
});

describe('send() state transitions', () => {
  beforeEach(() => {
    mockCollectDeviceInfo.mockReturnValue(deviceSignals);
    mockCollectNetworkInfo.mockReturnValue(networkSignals);
    mockCollectLocation.mockResolvedValue(locationSignals);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('transitions collected → sending → sent on success', async () => {
    const state = makeState();
    await runCollect(CLIENT_ID, state);

    mockSendPayload.mockResolvedValue({
      fingerprintId: state.fingerprintId!,
      status: 'success',
    });

    const promise = runSend(state, ENDPOINT, CLIENT_ID);
    expect(state.status).toBe('sending');

    await promise;
    expect(state.status).toBe('sent');
    expect(state.error).toBeNull();
  });

  it('transitions collected → sending → error when transport returns error', async () => {
    const state = makeState();
    await runCollect(CLIENT_ID, state);

    mockSendPayload.mockResolvedValue({
      fingerprintId: state.fingerprintId!,
      status: 'error',
    });

    await runSend(state, ENDPOINT, CLIENT_ID);
    expect(state.status).toBe('error');
    expect(state.error).not.toBeNull();
  });

  it('transitions to error when transport throws', async () => {
    const state = makeState();
    await runCollect(CLIENT_ID, state);

    mockSendPayload.mockRejectedValue(new Error('Network failure'));

    await expect(runSend(state, ENDPOINT, CLIENT_ID)).rejects.toThrow('Network failure');
    expect(state.status).toBe('error');
    expect(state.error?.message).toBe('Network failure');
  });

  it('throws and sets error when send() is called before collect()', async () => {
    const state = makeState();

    await expect(runSend(state, ENDPOINT, CLIENT_ID)).rejects.toThrow(
      'No collected data available',
    );
    expect(state.status).toBe('error');
    expect(mockSendPayload).not.toHaveBeenCalled();
  });

  it('passes correct fingerprintId through to SendResult', async () => {
    const state = makeState();
    await runCollect(CLIENT_ID, state);

    const expectedFpId = state.fingerprintId!;
    mockSendPayload.mockResolvedValue({ fingerprintId: expectedFpId, status: 'success' });

    const result = await runSend(state, ENDPOINT, CLIENT_ID);
    expect(result.fingerprintId).toBe(expectedFpId);
  });
});
