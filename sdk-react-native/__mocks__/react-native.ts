/**
 * Minimal React Native mock for Jest.
 *
 * Only the APIs actually consumed by the SDK are stubbed here. Add more as
 * needed when new native modules are integrated.
 */

export const Platform = {
  OS: 'ios' as const,
  select: <T extends Record<string, unknown>>(spec: T): T[keyof T] =>
    spec['ios'] as T[keyof T],
};

export const PermissionsAndroid = {
  PERMISSIONS: {
    ACCESS_FINE_LOCATION: 'android.permission.ACCESS_FINE_LOCATION',
  },
  RESULTS: {
    GRANTED: 'granted',
    DENIED: 'denied',
    NEVER_ASK_AGAIN: 'never_ask_again',
  },
  request: jest.fn().mockResolvedValue('granted'),
};

export const Geolocation = {
  getCurrentPosition: jest.fn(
    (
      success: (pos: {
        coords: { latitude: number; longitude: number; accuracy: number };
      }) => void,
    ) =>
      success({
        coords: { latitude: 37.7749, longitude: -122.4194, accuracy: 10 },
      }),
  ),
};

export default {
  Platform,
  PermissionsAndroid,
  Geolocation,
};
