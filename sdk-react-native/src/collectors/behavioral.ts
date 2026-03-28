/**
 * Behavioral signals collector for mobile.
 *
 * Captures three categories of signals:
 *  1. Accelerometer samples — motion patterns indicating human vs. bot/emulator.
 *     Real implementation: `react-native-sensors` (https://github.com/react-native-sensors/react-native-sensors)
 *       import { accelerometer } from 'react-native-sensors';
 *       subscription = accelerometer.subscribe(({ x, y, z }) => { ... });
 *
 *  2. Touch pressure — high pressure variance is a strong human signal.
 *     Real implementation: onPressIn handler with nativeEvent.force (iOS only).
 *       <TouchableWithoutFeedback onPressIn={(e) => recordPressure(e.nativeEvent.force)} />
 *
 *  3. Typing cadence — inter-arrival times between keystrokes.
 *     Real implementation: TextInput onKeyPress timestamp deltas.
 *       <TextInput onKeyPress={(e) => recordKeyEvent(e.nativeEvent.timestamp)} />
 */

export interface AccelerometerSample {
  x: number;
  y: number;
  z: number;
  timestamp: number;
}

export interface BehavioralData {
  accelerometerSamples: AccelerometerSample[];
  touchPressures: number[];
  keystrokeIntervals: number[];
}

export class BehavioralCollector {
  private accelerometerSamples: AccelerometerSample[] = [];
  private touchPressures: number[] = [];
  private lastKeystrokeTime: number | null = null;
  private keystrokeIntervals: number[] = [];
  private running = false;
  private sampleIntervalId: ReturnType<typeof setInterval> | null = null;

  /** Begin collecting accelerometer samples at ~10 Hz. */
  start(): void {
    if (this.running) return;
    this.running = true;

    // Simulated accelerometer sampling — replace with react-native-sensors subscription.
    this.sampleIntervalId = setInterval(() => {
      const sample: AccelerometerSample = {
        x: parseFloat((Math.random() * 2 - 1).toFixed(4)),
        y: parseFloat((Math.random() * 2 - 1).toFixed(4)),
        z: parseFloat((9.8 + (Math.random() * 0.4 - 0.2)).toFixed(4)),
        timestamp: Date.now(),
      };
      this.accelerometerSamples.push(sample);

      // Cap buffer to last 100 samples (~10 seconds at 10 Hz).
      if (this.accelerometerSamples.length > 100) {
        this.accelerometerSamples.shift();
      }
    }, 100);
  }

  /** Stop collecting accelerometer samples and release resources. */
  stop(): void {
    if (!this.running) return;
    this.running = false;

    if (this.sampleIntervalId !== null) {
      clearInterval(this.sampleIntervalId);
      this.sampleIntervalId = null;
    }
  }

  /**
   * Record a touch pressure event.
   * @param force Normalized force value in [0, 1]. On iOS, use nativeEvent.force.
   *              On Android, use MotionEvent.getPressure(). Pass 0 if unavailable.
   */
  recordTouchPressure(force: number): void {
    this.touchPressures.push(force);
    if (this.touchPressures.length > 50) {
      this.touchPressures.shift();
    }
  }

  /**
   * Record a keystroke event.
   * @param timestamp Epoch timestamp in milliseconds. Pass Date.now() if the
   *                  platform does not expose a native timestamp.
   */
  recordKeystroke(timestamp: number = Date.now()): void {
    if (this.lastKeystrokeTime !== null) {
      const interval = timestamp - this.lastKeystrokeTime;
      if (interval > 0 && interval < 5000) {
        // Ignore gaps > 5 s (pauses between fields).
        this.keystrokeIntervals.push(interval);
        if (this.keystrokeIntervals.length > 100) {
          this.keystrokeIntervals.shift();
        }
      }
    }
    this.lastKeystrokeTime = timestamp;
  }

  /**
   * Return a snapshot of all collected behavioral data.
   * Safe to call at any time; returns empty arrays when no data has been collected.
   */
  getData(): BehavioralData {
    return {
      accelerometerSamples: [...this.accelerometerSamples],
      touchPressures: [...this.touchPressures],
      keystrokeIntervals: [...this.keystrokeIntervals],
    };
  }

  /**
   * Serialize behavioral data into the flat signals map expected by the backend.
   */
  getSignals(): Record<string, string> {
    const data = this.getData();

    const avgAccelMagnitude =
      data.accelerometerSamples.length > 0
        ? (
            data.accelerometerSamples.reduce(
              (sum, s) =>
                sum + Math.sqrt(s.x * s.x + s.y * s.y + s.z * s.z),
              0,
            ) / data.accelerometerSamples.length
          ).toFixed(4)
        : '0';

    const avgPressure =
      data.touchPressures.length > 0
        ? (
            data.touchPressures.reduce((a, b) => a + b, 0) /
            data.touchPressures.length
          ).toFixed(4)
        : '0';

    const avgKeystrokeInterval =
      data.keystrokeIntervals.length > 0
        ? Math.round(
            data.keystrokeIntervals.reduce((a, b) => a + b, 0) /
              data.keystrokeIntervals.length,
          ).toString()
        : '0';

    return {
      'behavioral.accelSampleCount': String(data.accelerometerSamples.length),
      'behavioral.avgAccelMagnitude': avgAccelMagnitude,
      'behavioral.touchPressureCount': String(data.touchPressures.length),
      'behavioral.avgTouchPressure': avgPressure,
      'behavioral.keystrokeCount': String(data.keystrokeIntervals.length),
      'behavioral.avgKeystrokeIntervalMs': avgKeystrokeInterval,
    };
  }

  /** Reset all collected data. */
  reset(): void {
    this.accelerometerSamples = [];
    this.touchPressures = [];
    this.keystrokeIntervals = [];
    this.lastKeystrokeTime = null;
  }
}
