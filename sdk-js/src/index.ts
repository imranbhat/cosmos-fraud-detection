import { FraudSDKConfig, CollectionResult, SendResult, SDKEvent, EventHandler } from './types';
import { storeConfig, getConfig, clearConfig } from './config';
import { generateFingerprint, BehavioralCollector } from './fingerprint';
import { sendSignals } from './transport';

// Module-level state
let _behavioralCollector: BehavioralCollector | null = null;
let _lastCollectionResult: CollectionResult | null = null;
const _eventHandlers: Map<SDKEvent, EventHandler[]> = new Map();
let _sessionTimer: ReturnType<typeof setTimeout> | null = null;

function emit(event: SDKEvent, data: unknown): void {
  const handlers = _eventHandlers.get(event) ?? [];
  for (const handler of handlers) {
    try {
      handler(data);
    } catch {
      // Swallow handler errors to avoid breaking SDK internals
    }
  }
}

export const FraudSDK = {
  /**
   * Initialize the SDK. Must be called before collect() or send().
   * Starts behavioral tracking unless DNT is enabled.
   */
  init(config: FraudSDKConfig): void {
    // storeConfig validates and persists the config
    storeConfig(config);

    // Tear down any previous instance before re-initializing
    if (_behavioralCollector) {
      _behavioralCollector.stop();
    }
    _lastCollectionResult = null;

    _behavioralCollector = new BehavioralCollector();
    _behavioralCollector.start();

    // Auto-destroy after sessionTimeout
    const { sessionTimeout = 30 * 60 * 1000 } = getConfig();
    if (_sessionTimer !== null) {
      clearTimeout(_sessionTimer);
    }
    _sessionTimer = setTimeout(() => {
      FraudSDK.destroy();
    }, sessionTimeout);
  },

  /**
   * Run all collectors and compute the fingerprint.
   * Emits 'collected' on success, 'error' on failure.
   */
  async collect(): Promise<CollectionResult> {
    try {
      const behavioral = _behavioralCollector?.getData();
      const result = await generateFingerprint({ behavioral });
      _lastCollectionResult = result;
      emit('collected', result);
      return result;
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      emit('error', error);
      throw error;
    }
  },

  /**
   * POST the collected signals to the configured endpoint.
   * Calls collect() automatically if not already collected.
   * Emits 'sent' on success/failure, 'error' on unexpected failure.
   */
  async send(): Promise<SendResult> {
    try {
      const config = getConfig();

      if (!_lastCollectionResult) {
        await FraudSDK.collect();
      }

      const collectionResult = _lastCollectionResult!;
      const behavioral = _behavioralCollector?.getData();

      const sendResult = await sendSignals(
        config.endpoint,
        config.clientId,
        collectionResult,
        behavioral
      );

      emit('sent', sendResult);
      return sendResult;
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      emit('error', error);
      throw error;
    }
  },

  /**
   * Subscribe to SDK events: 'error' | 'collected' | 'sent'
   */
  on(event: SDKEvent, handler: EventHandler): void {
    const existing = _eventHandlers.get(event) ?? [];
    _eventHandlers.set(event, [...existing, handler]);
  },

  /**
   * Stop behavioral tracking, clear session timer, and reset state.
   */
  destroy(): void {
    if (_sessionTimer !== null) {
      clearTimeout(_sessionTimer);
      _sessionTimer = null;
    }
    if (_behavioralCollector) {
      _behavioralCollector.stop();
      _behavioralCollector = null;
    }
    _lastCollectionResult = null;
    _eventHandlers.clear();
    clearConfig();
  },
};

// Also export types for library consumers
export type { FraudSDKConfig, CollectionResult, SendResult, SDKEvent, EventHandler };
export type { BehavioralSignals } from './types';
