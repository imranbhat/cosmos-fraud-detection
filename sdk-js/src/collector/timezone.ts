export interface TimezoneSignals {
  offset: number;
  ianaTimezone: string;
}

export function collectTimezoneSignals(): TimezoneSignals {
  const offset = new Date().getTimezoneOffset();
  let ianaTimezone = 'unknown';

  try {
    ianaTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    // Intl not supported
  }

  return { offset, ianaTimezone };
}

export function serializeTimezoneSignals(signals: TimezoneSignals): Record<string, string> {
  return {
    'tz.offset': String(signals.offset),
    'tz.iana': signals.ianaTimezone,
  };
}
