export interface BrowserSignals {
  userAgent: string;
  platform: string;
  language: string;
  cookiesEnabled: boolean;
  doNotTrack: string | null;
  hardwareConcurrency: number;
  maxTouchPoints: number;
}

export function collectBrowserSignals(): BrowserSignals {
  const nav = navigator;
  return {
    userAgent: nav.userAgent,
    platform: nav.platform,
    language: nav.language,
    cookiesEnabled: nav.cookieEnabled,
    doNotTrack: nav.doNotTrack ?? null,
    hardwareConcurrency: nav.hardwareConcurrency ?? 0,
    maxTouchPoints: nav.maxTouchPoints ?? 0,
  };
}

export function serializeBrowserSignals(signals: BrowserSignals): Record<string, string> {
  return {
    'browser.userAgent': signals.userAgent,
    'browser.platform': signals.platform,
    'browser.language': signals.language,
    'browser.cookiesEnabled': String(signals.cookiesEnabled),
    'browser.doNotTrack': signals.doNotTrack ?? 'unspecified',
    'browser.hardwareConcurrency': String(signals.hardwareConcurrency),
    'browser.maxTouchPoints': String(signals.maxTouchPoints),
  };
}
