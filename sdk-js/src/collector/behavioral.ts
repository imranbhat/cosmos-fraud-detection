import { BehavioralSignals } from '../types';

const MOUSE_SAMPLE_MS = 50;
const TOUCH_SAMPLE_MS = 50;
const MAX_MOUSE_POINTS = 200;
const MAX_KEYSTROKES = 100;
const MAX_TOUCH_POINTS = 200;

export class BehavioralCollector {
  private mouseMovements: Array<{ x: number; y: number; t: number }> = [];
  private keystrokeIntervals: number[] = [];
  private touchEvents: Array<{ x: number; y: number; t: number; force?: number }> = [];

  private lastMouseSample = 0;
  private lastTouchSample = 0;
  private lastKeydownTime = 0;
  private dntEnabled = false;
  private running = false;

  // Bound listeners kept as references for removeEventListener
  private readonly _onMouseMove: (e: MouseEvent) => void;
  private readonly _onKeyDown: (e: KeyboardEvent) => void;
  private readonly _onKeyUp: (e: KeyboardEvent) => void;
  private readonly _onTouchStart: (e: TouchEvent) => void;
  private readonly _onTouchMove: (e: TouchEvent) => void;

  constructor() {
    this._onMouseMove = this.handleMouseMove.bind(this);
    this._onKeyDown = this.handleKeyDown.bind(this);
    this._onKeyUp = this.handleKeyUp.bind(this);
    this._onTouchStart = this.handleTouchEvent.bind(this);
    this._onTouchMove = this.handleTouchEvent.bind(this);
  }

  start(): void {
    if (this.running) return;

    // Respect Do Not Track
    this.dntEnabled = navigator.doNotTrack === '1';
    if (this.dntEnabled) return;

    this.running = true;

    document.addEventListener('mousemove', this._onMouseMove, { passive: true });
    document.addEventListener('keydown', this._onKeyDown, { passive: true });
    document.addEventListener('keyup', this._onKeyUp, { passive: true });
    document.addEventListener('touchstart', this._onTouchStart, { passive: true });
    document.addEventListener('touchmove', this._onTouchMove, { passive: true });
  }

  stop(): void {
    if (!this.running) return;
    this.running = false;

    document.removeEventListener('mousemove', this._onMouseMove);
    document.removeEventListener('keydown', this._onKeyDown);
    document.removeEventListener('keyup', this._onKeyUp);
    document.removeEventListener('touchstart', this._onTouchStart);
    document.removeEventListener('touchmove', this._onTouchMove);
  }

  getData(): BehavioralSignals {
    return {
      mouseMovements: [...this.mouseMovements],
      keystrokeIntervals: [...this.keystrokeIntervals],
      touchEvents: [...this.touchEvents],
    };
  }

  reset(): void {
    this.mouseMovements = [];
    this.keystrokeIntervals = [];
    this.touchEvents = [];
    this.lastMouseSample = 0;
    this.lastTouchSample = 0;
    this.lastKeydownTime = 0;
  }

  private handleMouseMove(e: MouseEvent): void {
    const now = Date.now();
    if (now - this.lastMouseSample < MOUSE_SAMPLE_MS) return;
    this.lastMouseSample = now;

    this.pushCircular(
      this.mouseMovements,
      { x: Math.round(e.clientX), y: Math.round(e.clientY), t: now },
      MAX_MOUSE_POINTS
    );
  }

  private handleKeyDown(e: KeyboardEvent): void {
    // Only record timing, not key identity (privacy-preserving)
    void e;
    this.lastKeydownTime = Date.now();
  }

  private handleKeyUp(_e: KeyboardEvent): void {
    if (this.lastKeydownTime === 0) return;
    const interval = Date.now() - this.lastKeydownTime;
    this.pushCircular(this.keystrokeIntervals, interval, MAX_KEYSTROKES);
    this.lastKeydownTime = 0;
  }

  private handleTouchEvent(e: TouchEvent): void {
    const now = Date.now();
    if (now - this.lastTouchSample < TOUCH_SAMPLE_MS) return;
    this.lastTouchSample = now;

    const touch = e.touches[0];
    if (!touch) return;

    const point: { x: number; y: number; t: number; force?: number } = {
      x: Math.round(touch.clientX),
      y: Math.round(touch.clientY),
      t: now,
    };

    // force is a non-standard but widely supported property
    const force = (touch as Touch & { force?: number }).force;
    if (typeof force === 'number') {
      point.force = force;
    }

    this.pushCircular(this.touchEvents, point, MAX_TOUCH_POINTS);
  }

  private pushCircular<T>(buffer: T[], item: T, maxSize: number): void {
    if (buffer.length >= maxSize) {
      buffer.shift();
    }
    buffer.push(item);
  }
}
