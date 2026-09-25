/**
 * Engine audio, synthesised with WebAudio.
 *
 * No audio files. The start-up sweep and the idle are generated from oscillators
 * and filtered noise, which keeps the cinematic route free of binary assets and
 * means the idle can track vehicle speed continuously instead of crossfading
 * between samples.
 *
 * Three rules, all from the brief:
 *
 *  1. Nothing plays without an explicit user gesture. Browsers block it anyway,
 *     but more importantly a site that makes noise at someone unprompted is a site
 *     they close.
 *  2. Audio never gates navigation. If the context cannot start, every visual beat
 *     still runs and the sequence is unaffected.
 *  3. The idle is restrained and it stops when the car stops. A looping engine
 *     drone is the fastest way to make a mute button the first thing anyone finds.
 */

type AudioState = 'UNAVAILABLE' | 'LOCKED' | 'READY' | 'MUTED';

class EngineAudio {
  private context: AudioContext | null = null;
  private master: GainNode | null = null;
  private idleGain: GainNode | null = null;
  private oscillators: OscillatorNode[] = [];
  private state: AudioState = 'LOCKED';
  private started = false;

  getState(): AudioState {
    return this.state;
  }

  /**
   * Called from a real user gesture (click, key, scroll).
   *
   * Returns false when audio is simply not available — the caller treats that as a
   * normal outcome, not an error.
   */
  async unlock(): Promise<boolean> {
    if (this.state === 'READY') return true;

    try {
      const Ctor =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (Ctor === undefined) {
        this.state = 'UNAVAILABLE';
        return false;
      }

      this.context ??= new Ctor();
      if (this.context.state === 'suspended') await this.context.resume();

      if (this.master === null) {
        this.master = this.context.createGain();
        this.master.gain.value = 0.55;
        this.master.connect(this.context.destination);
      }

      this.state = 'READY';
      return true;
    } catch {
      this.state = 'UNAVAILABLE';
      return false;
    }
  }

  /**
   * The ignition moment: a filtered noise crank, then a pitch sweep settling into
   * the idle. Roughly 1.6 seconds, shaped to land on the headlight beat.
   */
  startEngine(): void {
    if (this.state !== 'READY' || this.context === null || this.master === null) return;
    if (this.started) return;
    this.started = true;

    const ctx = this.context;
    const now = ctx.currentTime;

    /* --- crank: a short burst of low-passed noise --------------------------- */
    const noiseLength = Math.floor(ctx.sampleRate * 0.55);
    const noiseBuffer = ctx.createBuffer(1, noiseLength, ctx.sampleRate);
    const channel = noiseBuffer.getChannelData(0);
    for (let i = 0; i < noiseLength; i += 1) {
      channel[i] = (Math.random() * 2 - 1) * (1 - i / noiseLength);
    }

    const noise = ctx.createBufferSource();
    noise.buffer = noiseBuffer;

    const noiseFilter = ctx.createBiquadFilter();
    noiseFilter.type = 'lowpass';
    noiseFilter.frequency.value = 420;

    const noiseGain = ctx.createGain();
    noiseGain.gain.setValueAtTime(0.0001, now);
    noiseGain.gain.exponentialRampToValueAtTime(0.35, now + 0.06);
    noiseGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.5);

    noise.connect(noiseFilter).connect(noiseGain).connect(this.master);
    noise.start(now);

    /* --- catch: a sweep from a low crank up to idle -------------------------- */
    const sweep = ctx.createOscillator();
    sweep.type = 'sawtooth';
    sweep.frequency.setValueAtTime(34, now + 0.1);
    sweep.frequency.exponentialRampToValueAtTime(128, now + 0.62);
    sweep.frequency.exponentialRampToValueAtTime(62, now + 1.25);

    const sweepFilter = ctx.createBiquadFilter();
    sweepFilter.type = 'lowpass';
    sweepFilter.frequency.setValueAtTime(320, now);
    sweepFilter.frequency.linearRampToValueAtTime(1500, now + 0.6);
    sweepFilter.frequency.linearRampToValueAtTime(620, now + 1.3);

    const sweepGain = ctx.createGain();
    sweepGain.gain.setValueAtTime(0.0001, now + 0.1);
    sweepGain.gain.exponentialRampToValueAtTime(0.42, now + 0.35);
    sweepGain.gain.exponentialRampToValueAtTime(0.0001, now + 1.5);

    sweep.connect(sweepFilter).connect(sweepGain).connect(this.master);
    sweep.start(now + 0.1);
    sweep.stop(now + 1.6);

    /* --- idle: two detuned saws, deliberately quiet -------------------------- */
    this.idleGain = ctx.createGain();
    this.idleGain.gain.setValueAtTime(0.0001, now + 0.8);
    this.idleGain.gain.exponentialRampToValueAtTime(0.14, now + 1.6);

    const idleFilter = ctx.createBiquadFilter();
    idleFilter.type = 'lowpass';
    idleFilter.frequency.value = 340;
    idleFilter.Q.value = 3;

    for (const [frequency, detune] of [
      [54, 0],
      [81, 8],
      [27, -6],
    ] as const) {
      const osc = ctx.createOscillator();
      osc.type = 'sawtooth';
      osc.frequency.value = frequency;
      osc.detune.value = detune;
      osc.connect(idleFilter);
      osc.start(now + 0.8);
      this.oscillators.push(osc);
    }

    idleFilter.connect(this.idleGain).connect(this.master);
  }

  /**
   * Track vehicle speed.
   *
   * The idle rises in pitch and opens up as the car accelerates. Small ramps
   * rather than direct assignment, so a fast scroll does not produce zipper noise.
   */
  setSpeed(speed: number): void {
    if (this.context === null || this.oscillators.length === 0) return;
    const now = this.context.currentTime;
    const factor = 1 + Math.min(speed, 3) * 0.55;

    for (const [index, osc] of this.oscillators.entries()) {
      const base = [54, 81, 27][index] ?? 54;
      osc.frequency.setTargetAtTime(base * factor, now, 0.12);
    }
    if (this.idleGain !== null) {
      this.idleGain.gain.setTargetAtTime(0.14 + Math.min(speed, 3) * 0.05, now, 0.2);
    }
  }

  setMuted(muted: boolean): void {
    if (this.master === null || this.context === null) return;
    this.master.gain.setTargetAtTime(muted ? 0 : 0.55, this.context.currentTime, 0.08);
    this.state = muted ? 'MUTED' : 'READY';
  }

  /** Full teardown on route exit. An AudioContext left running holds hardware open. */
  dispose(): void {
    for (const osc of this.oscillators) {
      try {
        osc.stop();
      } catch {
        // Already stopped.
      }
    }
    this.oscillators = [];
    this.idleGain = null;
    this.master = null;
    this.started = false;

    void this.context?.close().catch(() => undefined);
    this.context = null;
    this.state = 'LOCKED';
  }
}

export const engineAudio = new EngineAudio();
