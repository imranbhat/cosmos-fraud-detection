/**
 * Location information collector.
 *
 * In a real React Native application, this module would use the built-in
 * `react-native` Geolocation API or `@react-native-community/geolocation`:
 *
 *   import Geolocation from '@react-native-community/geolocation';
 *   // iOS: request NSLocationWhenInUseUsageDescription in Info.plist
 *   // Android: request ACCESS_FINE_LOCATION in AndroidManifest.xml
 *   Geolocation.getCurrentPosition(
 *     (pos) => resolve({ latitude, longitude, accuracy }),
 *     (err) => resolve(null),  // permission denied or unavailable
 *   );
 */

export interface LocationInfo {
  latitude: string;
  longitude: string;
  accuracy: string;
}

/**
 * Collects approximate location signals, gated by runtime permission.
 *
 * Returns null when permission has not been granted or the platform cannot
 * provide a fix, so callers must handle the null case gracefully.
 */
export async function collectLocation(): Promise<Record<string, string> | null> {
  const permitted = await requestLocationPermission();
  if (!permitted) {
    return null;
  }

  // Simulated position — replace with real Geolocation call in production.
  const info: LocationInfo = {
    latitude: '37.7749',
    longitude: '-122.4194',
    accuracy: '10.0',
  };

  return {
    'location.latitude': info.latitude,
    'location.longitude': info.longitude,
    'location.accuracy': info.accuracy,
  };
}

/**
 * Simulates requesting location permission.
 *
 * In production, replace with the appropriate platform call:
 *   import { PermissionsAndroid, Platform } from 'react-native';
 *   if (Platform.OS === 'android') {
 *     const granted = await PermissionsAndroid.request(
 *       PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
 *     );
 *     return granted === PermissionsAndroid.RESULTS.GRANTED;
 *   }
 *   return true; // iOS permission is declared statically in Info.plist
 */
async function requestLocationPermission(): Promise<boolean> {
  // Always returns true in the simulated environment.
  return Promise.resolve(true);
}
