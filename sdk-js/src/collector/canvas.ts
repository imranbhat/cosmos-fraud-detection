import { sha256Hex } from '../utils/hash';

export interface CanvasSignals {
  hash: string;
}

export async function collectCanvasSignals(): Promise<CanvasSignals> {
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 200;
    canvas.height = 50;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return { hash: 'canvas-unavailable' };
    }

    // Draw text with specific styling to produce a unique rendering
    ctx.textBaseline = 'top';
    ctx.font = '14px Arial, sans-serif';
    ctx.fillStyle = '#f60';
    ctx.fillRect(0, 0, 200, 50);

    ctx.fillStyle = '#069';
    ctx.fillText('FraudSDK canvas fp', 2, 2);

    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)';
    ctx.fillText('FraudSDK canvas fp', 4, 4);

    // Draw geometric shapes for additional entropy
    ctx.beginPath();
    ctx.arc(100, 25, 20, 0, Math.PI * 2, true);
    ctx.fillStyle = 'rgba(0, 0, 200, 0.5)';
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(10, 40);
    ctx.lineTo(190, 40);
    ctx.strokeStyle = 'rgba(255, 0, 0, 0.5)';
    ctx.lineWidth = 2;
    ctx.stroke();

    const dataUrl = canvas.toDataURL('image/png');
    const hash = await sha256Hex(dataUrl);

    return { hash };
  } catch {
    return { hash: 'canvas-error' };
  }
}

export function serializeCanvasSignals(signals: CanvasSignals): Record<string, string> {
  return {
    'canvas.hash': signals.hash,
  };
}
