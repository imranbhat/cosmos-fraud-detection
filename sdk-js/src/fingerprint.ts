import { collectBrowserSignals, serializeBrowserSignals } from './collector/browser';
import { collectScreenSignals, serializeScreenSignals } from './collector/screen';
import { collectCanvasSignals, serializeCanvasSignals } from './collector/canvas';
import { collectWebGLSignals, serializeWebGLSignals } from './collector/webgl';
import { collectTimezoneSignals, serializeTimezoneSignals } from './collector/timezone';
import { BehavioralCollector } from './collector/behavioral';
import { sha256Hex } from './utils/hash';
import { CollectionResult, BehavioralSignals } from './types';

export interface FingerprintOptions {
  behavioral?: BehavioralSignals;
}

export async function generateFingerprint(
  options: FingerprintOptions = {}
): Promise<CollectionResult> {
  const [browser, screen, canvas, webgl, timezone] = await Promise.all([
    Promise.resolve(collectBrowserSignals()),
    Promise.resolve(collectScreenSignals()),
    collectCanvasSignals(),
    collectWebGLSignals(),
    Promise.resolve(collectTimezoneSignals()),
  ]);

  const signals: Record<string, string> = {
    ...serializeBrowserSignals(browser),
    ...serializeScreenSignals(screen),
    ...serializeCanvasSignals(canvas),
    ...serializeWebGLSignals(webgl),
    ...serializeTimezoneSignals(timezone),
  };

  // Add behavioral signal summary (counts only — raw events stay in payload)
  if (options.behavioral) {
    signals['behavioral.mouseMovements'] = String(options.behavioral.mouseMovements.length);
    signals['behavioral.keystrokeIntervals'] = String(options.behavioral.keystrokeIntervals.length);
    signals['behavioral.touchEvents'] = String(options.behavioral.touchEvents.length);
  }

  // Produce stable fingerprint: sort keys so ordering doesn't affect the hash
  const stableInput = Object.keys(signals)
    .sort()
    .map((k) => `${k}=${signals[k]}`)
    .join('|');

  const fingerprintId = await sha256Hex(stableInput);

  return {
    fingerprintId,
    signals,
    timestamp: Date.now(),
  };
}

// Re-export BehavioralCollector so the main entry point can use it
export { BehavioralCollector };
