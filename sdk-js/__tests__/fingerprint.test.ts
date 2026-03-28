/**
 * Tests for fingerprint generation.
 * Uses jsdom environment (configured in package.json jest config).
 */

// Polyfill SubtleCrypto for jsdom (Node 18+ has globalThis.crypto)
import { webcrypto } from 'crypto';
if (!globalThis.crypto) {
  // @ts-expect-error - polyfill for test environment
  globalThis.crypto = webcrypto;
}

import { generateFingerprint } from '../src/fingerprint';

// Mock canvas getContext to return a minimal stub
beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = jest.fn().mockReturnValue({
    textBaseline: '',
    font: '',
    fillStyle: '',
    fillRect: jest.fn(),
    fillText: jest.fn(),
    beginPath: jest.fn(),
    arc: jest.fn(),
    fill: jest.fn(),
    moveTo: jest.fn(),
    lineTo: jest.fn(),
    stroke: jest.fn(),
    lineWidth: 0,
    strokeStyle: '',
  });

  HTMLCanvasElement.prototype.toDataURL = jest
    .fn()
    .mockReturnValue('data:image/png;base64,canvas-data');
});

describe('generateFingerprint', () => {
  it('returns a CollectionResult with required fields', async () => {
    const result = await generateFingerprint();

    expect(result).toHaveProperty('fingerprintId');
    expect(result).toHaveProperty('signals');
    expect(result).toHaveProperty('timestamp');
    expect(typeof result.fingerprintId).toBe('string');
    expect(result.fingerprintId.length).toBeGreaterThan(0);
    expect(typeof result.signals).toBe('object');
    expect(result.timestamp).toBeLessThanOrEqual(Date.now());
  });

  it('produces a consistent hash for the same inputs', async () => {
    const result1 = await generateFingerprint();
    const result2 = await generateFingerprint();

    // In a stable jsdom environment with mocked canvas, same signals => same fingerprint
    expect(result1.fingerprintId).toBe(result2.fingerprintId);
  });

  it('produces a different hash when behavioral signals differ', async () => {
    const result1 = await generateFingerprint({ behavioral: undefined });
    const result2 = await generateFingerprint({
      behavioral: {
        mouseMovements: [{ x: 1, y: 2, t: 100 }],
        keystrokeIntervals: [50],
        touchEvents: [],
      },
    });

    // behavioral signal counts are mixed into signals, so hashes should differ
    expect(result1.fingerprintId).not.toBe(result2.fingerprintId);
  });

  it('includes expected signal namespaces', async () => {
    const result = await generateFingerprint();
    const keys = Object.keys(result.signals);

    const expectedPrefixes = [
      'browser.',
      'screen.',
      'canvas.',
      'webgl.',
      'tz.',
    ];

    for (const prefix of expectedPrefixes) {
      const hasPrefix = keys.some((k) => k.startsWith(prefix));
      expect(hasPrefix).toBe(true);
    }
  });

  it('includes behavioral counts when behavioral data is provided', async () => {
    const result = await generateFingerprint({
      behavioral: {
        mouseMovements: [{ x: 10, y: 20, t: 999 }],
        keystrokeIntervals: [80, 120],
        touchEvents: [],
      },
    });

    expect(result.signals['behavioral.mouseMovements']).toBe('1');
    expect(result.signals['behavioral.keystrokeIntervals']).toBe('2');
    expect(result.signals['behavioral.touchEvents']).toBe('0');
  });
});
