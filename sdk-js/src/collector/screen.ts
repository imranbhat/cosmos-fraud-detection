export interface ScreenSignals {
  width: number;
  height: number;
  colorDepth: number;
  pixelRatio: number;
}

export function collectScreenSignals(): ScreenSignals {
  return {
    width: screen.width,
    height: screen.height,
    colorDepth: screen.colorDepth,
    pixelRatio: window.devicePixelRatio ?? 1,
  };
}

export function serializeScreenSignals(signals: ScreenSignals): Record<string, string> {
  return {
    'screen.width': String(signals.width),
    'screen.height': String(signals.height),
    'screen.colorDepth': String(signals.colorDepth),
    'screen.pixelRatio': String(signals.pixelRatio),
  };
}
