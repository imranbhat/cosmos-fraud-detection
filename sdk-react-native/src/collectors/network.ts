/**
 * Network information collector.
 *
 * In a real React Native application, this module would use
 * `@react-native-community/netinfo` (https://github.com/react-native-netinfo/react-native-netinfo)
 * to obtain live values:
 *
 *   import NetInfo from '@react-native-community/netinfo';
 *   const state = await NetInfo.fetch();
 *   connectionType: state.type,
 *   isConnected: String(state.isConnected ?? false),
 *   carrier: state.details?.carrier ?? 'unknown',
 */

export interface NetworkInfo {
  connectionType: string;
  isConnected: string;
  carrier: string;
}

/**
 * Collects network context signals.
 *
 * Returns a flat Record<string, string> suitable for inclusion in the signals
 * map sent to the fraud-detection backend.
 */
export function collectNetworkInfo(): Record<string, string> {
  // Simulated values — replace with @react-native-community/netinfo calls in production.
  const info: NetworkInfo = {
    connectionType: 'wifi',
    isConnected: 'true',
    carrier: 'SimulatedCarrier',
  };

  return {
    'network.connectionType': info.connectionType,
    'network.isConnected': info.isConnected,
    'network.carrier': info.carrier,
  };
}
