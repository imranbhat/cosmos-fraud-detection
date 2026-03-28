/**
 * Device information collector.
 *
 * In a real React Native application, this module would use
 * `react-native-device-info` (https://github.com/react-native-device-info/react-native-device-info)
 * to obtain live values. The interface below mirrors that library's API so that
 * swapping in the real implementation is a one-line change per field.
 *
 * Example real implementation:
 *   import DeviceInfo from 'react-native-device-info';
 *   model: DeviceInfo.getModel(),
 *   brand: DeviceInfo.getBrand(),
 *   systemName: DeviceInfo.getSystemName(),
 *   systemVersion: DeviceInfo.getSystemVersion(),
 *   uniqueId: await DeviceInfo.getUniqueId(),
 *   isEmulator: String(await DeviceInfo.isEmulator()),
 */

export interface DeviceInfo {
  model: string;
  brand: string;
  systemName: string;
  systemVersion: string;
  uniqueId: string;
  isEmulator: string;
}

/**
 * Collects device fingerprint signals.
 *
 * Returns a flat Record<string, string> suitable for inclusion in the signals
 * map sent to the fraud-detection backend.
 */
export function collectDeviceInfo(): Record<string, string> {
  // Simulated values — replace with react-native-device-info calls in production.
  const info: DeviceInfo = {
    model: 'Simulated Model',
    brand: 'Simulated Brand',
    systemName: 'iOS',
    systemVersion: '17.0',
    uniqueId: generateSimulatedUniqueId(),
    isEmulator: 'false',
  };

  return {
    'device.model': info.model,
    'device.brand': info.brand,
    'device.systemName': info.systemName,
    'device.systemVersion': info.systemVersion,
    'device.uniqueId': info.uniqueId,
    'device.isEmulator': info.isEmulator,
  };
}

/**
 * Produces a stable pseudo-unique device ID for the simulated environment.
 * In production this is replaced by DeviceInfo.getUniqueId().
 */
function generateSimulatedUniqueId(): string {
  return 'simulated-uid-' + Math.random().toString(36).slice(2, 11);
}
