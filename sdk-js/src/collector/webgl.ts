import { sha256Hex } from '../utils/hash';

export interface WebGLSignals {
  renderer: string;
  vendor: string;
  extensions: string;
  hash: string;
}

export async function collectWebGLSignals(): Promise<WebGLSignals> {
  try {
    const canvas = document.createElement('canvas');
    const gl =
      (canvas.getContext('webgl') as WebGLRenderingContext | null) ??
      (canvas.getContext('experimental-webgl') as WebGLRenderingContext | null);

    if (!gl) {
      return {
        renderer: 'webgl-unavailable',
        vendor: 'webgl-unavailable',
        extensions: '',
        hash: 'webgl-unavailable',
      };
    }

    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');

    const renderer = debugInfo
      ? (gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) as string) ?? 'unknown'
      : 'unknown';

    const vendor = debugInfo
      ? (gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) as string) ?? 'unknown'
      : 'unknown';

    const extensions = gl.getSupportedExtensions()?.join(',') ?? '';

    const raw = `${renderer}|${vendor}|${extensions}`;
    const hash = await sha256Hex(raw);

    return { renderer, vendor, extensions, hash };
  } catch {
    return {
      renderer: 'webgl-error',
      vendor: 'webgl-error',
      extensions: '',
      hash: 'webgl-error',
    };
  }
}

export function serializeWebGLSignals(signals: WebGLSignals): Record<string, string> {
  return {
    'webgl.renderer': signals.renderer,
    'webgl.vendor': signals.vendor,
    'webgl.hash': signals.hash,
  };
}
