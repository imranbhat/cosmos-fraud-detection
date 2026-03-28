import { collectDeviceInfo } from '../src/collectors/device';
import { collectNetworkInfo } from '../src/collectors/network';
import { collectLocation } from '../src/collectors/location';
import { BehavioralCollector } from '../src/collectors/behavioral';

// ---------------------------------------------------------------------------
// Device collector
// ---------------------------------------------------------------------------

describe('collectDeviceInfo', () => {
  it('returns a Record with all expected device signal keys', () => {
    const result = collectDeviceInfo();

    expect(result).toHaveProperty('device.model');
    expect(result).toHaveProperty('device.brand');
    expect(result).toHaveProperty('device.systemName');
    expect(result).toHaveProperty('device.systemVersion');
    expect(result).toHaveProperty('device.uniqueId');
    expect(result).toHaveProperty('device.isEmulator');
  });

  it('returns string values for all keys', () => {
    const result = collectDeviceInfo();
    Object.values(result).forEach((value) => {
      expect(typeof value).toBe('string');
    });
  });

  it('uniqueId is non-empty', () => {
    const result = collectDeviceInfo();
    expect(result['device.uniqueId'].length).toBeGreaterThan(0);
  });

  it('isEmulator is a boolean string', () => {
    const result = collectDeviceInfo();
    expect(['true', 'false']).toContain(result['device.isEmulator']);
  });
});

// ---------------------------------------------------------------------------
// Network collector
// ---------------------------------------------------------------------------

describe('collectNetworkInfo', () => {
  it('returns a Record with all expected network signal keys', () => {
    const result = collectNetworkInfo();

    expect(result).toHaveProperty('network.connectionType');
    expect(result).toHaveProperty('network.isConnected');
    expect(result).toHaveProperty('network.carrier');
  });

  it('returns string values for all keys', () => {
    const result = collectNetworkInfo();
    Object.values(result).forEach((value) => {
      expect(typeof value).toBe('string');
    });
  });

  it('isConnected is a boolean string', () => {
    const result = collectNetworkInfo();
    expect(['true', 'false']).toContain(result['network.isConnected']);
  });
});

// ---------------------------------------------------------------------------
// Location collector
// ---------------------------------------------------------------------------

describe('collectLocation', () => {
  it('resolves to a Record with latitude, longitude, and accuracy when permitted', async () => {
    const result = await collectLocation();

    expect(result).not.toBeNull();
    expect(result).toHaveProperty('location.latitude');
    expect(result).toHaveProperty('location.longitude');
    expect(result).toHaveProperty('location.accuracy');
  });

  it('returns string values for all location keys', async () => {
    const result = await collectLocation();
    expect(result).not.toBeNull();
    Object.values(result!).forEach((value) => {
      expect(typeof value).toBe('string');
    });
  });

  it('latitude and longitude are parseable as numbers', async () => {
    const result = await collectLocation();
    expect(result).not.toBeNull();
    expect(Number.isFinite(parseFloat(result!['location.latitude']))).toBe(true);
    expect(Number.isFinite(parseFloat(result!['location.longitude']))).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Behavioral collector
// ---------------------------------------------------------------------------

describe('BehavioralCollector', () => {
  let collector: BehavioralCollector;

  beforeEach(() => {
    jest.useFakeTimers();
    collector = new BehavioralCollector();
  });

  afterEach(() => {
    collector.stop();
    jest.useRealTimers();
  });

  it('getData() returns empty arrays before start()', () => {
    const data = collector.getData();
    expect(data.accelerometerSamples).toEqual([]);
    expect(data.touchPressures).toEqual([]);
    expect(data.keystrokeIntervals).toEqual([]);
  });

  it('accumulates accelerometer samples after start()', () => {
    collector.start();
    jest.advanceTimersByTime(350); // ~3 samples at 100ms interval
    const data = collector.getData();
    expect(data.accelerometerSamples.length).toBeGreaterThanOrEqual(3);
  });

  it('accelerometer samples have the correct shape', () => {
    collector.start();
    jest.advanceTimersByTime(150);
    const data = collector.getData();
    expect(data.accelerometerSamples.length).toBeGreaterThan(0);
    const sample = data.accelerometerSamples[0];
    expect(typeof sample.x).toBe('number');
    expect(typeof sample.y).toBe('number');
    expect(typeof sample.z).toBe('number');
    expect(typeof sample.timestamp).toBe('number');
  });

  it('stop() halts accelerometer sampling', () => {
    collector.start();
    jest.advanceTimersByTime(200);
    const countBeforeStop = collector.getData().accelerometerSamples.length;
    collector.stop();
    jest.advanceTimersByTime(500);
    const countAfterStop = collector.getData().accelerometerSamples.length;
    expect(countAfterStop).toBe(countBeforeStop);
  });

  it('recordTouchPressure() stores pressure values', () => {
    collector.recordTouchPressure(0.5);
    collector.recordTouchPressure(0.8);
    expect(collector.getData().touchPressures).toEqual([0.5, 0.8]);
  });

  it('recordKeystroke() calculates inter-arrival intervals', () => {
    collector.recordKeystroke(1000);
    collector.recordKeystroke(1150);
    collector.recordKeystroke(1320);
    const { keystrokeIntervals } = collector.getData();
    expect(keystrokeIntervals).toEqual([150, 170]);
  });

  it('getSignals() returns a flat Record<string, string>', () => {
    collector.recordTouchPressure(0.6);
    collector.recordKeystroke(1000);
    collector.recordKeystroke(1200);
    const signals = collector.getSignals();

    expect(signals).toHaveProperty('behavioral.accelSampleCount');
    expect(signals).toHaveProperty('behavioral.avgAccelMagnitude');
    expect(signals).toHaveProperty('behavioral.touchPressureCount');
    expect(signals).toHaveProperty('behavioral.avgTouchPressure');
    expect(signals).toHaveProperty('behavioral.keystrokeCount');
    expect(signals).toHaveProperty('behavioral.avgKeystrokeIntervalMs');
    Object.values(signals).forEach((v) => expect(typeof v).toBe('string'));
  });

  it('reset() clears all collected data', () => {
    collector.recordTouchPressure(0.5);
    collector.recordKeystroke(1000);
    collector.recordKeystroke(1200);
    collector.reset();
    const data = collector.getData();
    expect(data.touchPressures).toEqual([]);
    expect(data.keystrokeIntervals).toEqual([]);
  });
});
