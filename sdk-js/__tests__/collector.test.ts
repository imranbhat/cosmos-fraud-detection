/**
 * Tests for browser and screen collectors.
 */

import { collectBrowserSignals, serializeBrowserSignals } from '../src/collector/browser';
import { collectScreenSignals, serializeScreenSignals } from '../src/collector/screen';
import { collectTimezoneSignals, serializeTimezoneSignals } from '../src/collector/timezone';

// jsdom provides navigator and screen globals; customize them for these tests
describe('BrowserCollector', () => {
  beforeAll(() => {
    // jsdom sets these but we want deterministic values
    Object.defineProperty(navigator, 'userAgent', {
      value: 'TestBrowser/1.0',
      configurable: true,
    });
    Object.defineProperty(navigator, 'platform', {
      value: 'TestOS',
      configurable: true,
    });
    Object.defineProperty(navigator, 'language', {
      value: 'en-US',
      configurable: true,
    });
    Object.defineProperty(navigator, 'cookieEnabled', {
      value: true,
      configurable: true,
    });
    Object.defineProperty(navigator, 'hardwareConcurrency', {
      value: 8,
      configurable: true,
    });
    Object.defineProperty(navigator, 'maxTouchPoints', {
      value: 0,
      configurable: true,
    });
    Object.defineProperty(navigator, 'doNotTrack', {
      value: null,
      configurable: true,
    });
  });

  it('returns all expected fields', () => {
    const signals = collectBrowserSignals();

    expect(signals).toHaveProperty('userAgent');
    expect(signals).toHaveProperty('platform');
    expect(signals).toHaveProperty('language');
    expect(signals).toHaveProperty('cookiesEnabled');
    expect(signals).toHaveProperty('doNotTrack');
    expect(signals).toHaveProperty('hardwareConcurrency');
    expect(signals).toHaveProperty('maxTouchPoints');
  });

  it('returns correct mocked values', () => {
    const signals = collectBrowserSignals();

    expect(signals.userAgent).toBe('TestBrowser/1.0');
    expect(signals.platform).toBe('TestOS');
    expect(signals.language).toBe('en-US');
    expect(signals.cookiesEnabled).toBe(true);
    expect(signals.hardwareConcurrency).toBe(8);
    expect(signals.maxTouchPoints).toBe(0);
    expect(signals.doNotTrack).toBeNull();
  });

  it('serializes to string key-value pairs', () => {
    const signals = collectBrowserSignals();
    const serialized = serializeBrowserSignals(signals);

    expect(typeof serialized['browser.userAgent']).toBe('string');
    expect(typeof serialized['browser.platform']).toBe('string');
    expect(typeof serialized['browser.cookiesEnabled']).toBe('string');
    expect(serialized['browser.cookiesEnabled']).toBe('true');
    expect(serialized['browser.hardwareConcurrency']).toBe('8');
    expect(serialized['browser.doNotTrack']).toBe('unspecified');
  });

  it('serializes doNotTrack="1" correctly', () => {
    const serialized = serializeBrowserSignals({
      userAgent: 'ua',
      platform: 'p',
      language: 'en',
      cookiesEnabled: false,
      doNotTrack: '1',
      hardwareConcurrency: 4,
      maxTouchPoints: 0,
    });
    expect(serialized['browser.doNotTrack']).toBe('1');
  });
});

describe('ScreenCollector', () => {
  beforeAll(() => {
    Object.defineProperty(window.screen, 'width', { value: 1920, configurable: true });
    Object.defineProperty(window.screen, 'height', { value: 1080, configurable: true });
    Object.defineProperty(window.screen, 'colorDepth', { value: 24, configurable: true });
    Object.defineProperty(window, 'devicePixelRatio', { value: 2, configurable: true });
  });

  it('returns all expected fields', () => {
    const signals = collectScreenSignals();

    expect(signals).toHaveProperty('width');
    expect(signals).toHaveProperty('height');
    expect(signals).toHaveProperty('colorDepth');
    expect(signals).toHaveProperty('pixelRatio');
  });

  it('returns valid dimensions', () => {
    const signals = collectScreenSignals();

    expect(signals.width).toBeGreaterThan(0);
    expect(signals.height).toBeGreaterThan(0);
    expect(signals.colorDepth).toBeGreaterThan(0);
    expect(signals.pixelRatio).toBeGreaterThan(0);
  });

  it('returns correct mocked values', () => {
    const signals = collectScreenSignals();

    expect(signals.width).toBe(1920);
    expect(signals.height).toBe(1080);
    expect(signals.colorDepth).toBe(24);
    expect(signals.pixelRatio).toBe(2);
  });

  it('serializes to string key-value pairs', () => {
    const signals = collectScreenSignals();
    const serialized = serializeScreenSignals(signals);

    expect(serialized['screen.width']).toBe('1920');
    expect(serialized['screen.height']).toBe('1080');
    expect(serialized['screen.colorDepth']).toBe('24');
    expect(serialized['screen.pixelRatio']).toBe('2');
  });
});

describe('TimezoneCollector', () => {
  it('returns offset and ianaTimezone', () => {
    const signals = collectTimezoneSignals();

    expect(signals).toHaveProperty('offset');
    expect(signals).toHaveProperty('ianaTimezone');
    expect(typeof signals.offset).toBe('number');
    expect(typeof signals.ianaTimezone).toBe('string');
  });

  it('serializes correctly', () => {
    const signals = collectTimezoneSignals();
    const serialized = serializeTimezoneSignals(signals);

    expect(typeof serialized['tz.offset']).toBe('string');
    expect(typeof serialized['tz.iana']).toBe('string');
    expect(serialized['tz.offset']).toBe(String(signals.offset));
    expect(serialized['tz.iana']).toBe(signals.ianaTimezone);
  });
});
