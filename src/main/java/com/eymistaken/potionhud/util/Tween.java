package com.eymistaken.potionhud.util;

/**
 * A float that eases toward a target over wall-clock time, so motion looks identical at
 * 30 FPS and at 240.
 *
 * <p>Deliberately our own rather than Eymistaken's HUD's {@code Anim}: the views that use
 * it have to keep working when that mod is not installed, and a single import of one of
 * its classes would stop the whole mod loading. Same idea, same feel — the easing curves
 * below match the ones the HUD animates its own modules with, so the two sit side by side
 * without one looking snappier than the other.
 */
public final class Tween {

    /** Maps normalized progress {@code t} in {@code [0,1]} to an eased value. */
    public interface Curve {
        float apply(float t);
    }

    private float value;
    private float start;
    private float target;
    private long startNanos;
    private long durationNanos;
    private boolean initialized;

    public Tween(float initial) {
        this.value = initial;
        this.start = initial;
        this.target = initial;
    }

    public float get() {
        return value;
    }

    /** Jump straight to {@code v}, cancelling any tween in flight. */
    public void snap(float v) {
        this.value = v;
        this.start = v;
        this.target = v;
        this.durationNanos = 0L;
        this.initialized = true;
    }

    /**
     * Advance toward {@code target}. A new target starts a fresh tween from wherever the
     * value currently is, so redirecting mid-flight never jumps.
     *
     * @param durationSeconds tween length; {@code <= 0} snaps
     */
    public float update(float target, float durationSeconds, Curve curve) {
        long now = System.nanoTime();

        if (!initialized) {
            // First frame has nothing to ease from; start settled rather than sliding
            // in from zero the moment the element appears.
            this.value = target;
            this.start = target;
            this.target = target;
            this.initialized = true;
            return value;
        }

        if (target != this.target) {
            this.start = this.value;
            this.target = target;
            this.startNanos = now;
            this.durationNanos = (long) (durationSeconds * 1_000_000_000L);
        }

        if (durationNanos <= 0L) {
            value = target;
            return value;
        }

        float t = (now - startNanos) / (float) durationNanos;
        if (t >= 1f) {
            value = target;
            return value;
        }
        value = start + (target - start) * curve.apply(clamp01(t));
        return value;
    }

    // --- Curves ---------------------------------------------------------------

    public static float clamp01(float t) {
        if (t < 0f) return 0f;
        return t > 1f ? 1f : t;
    }

    /** Symmetric ease, gentle in and out. Good for fades. */
    public static float sineInOut(float t) {
        t = clamp01(t);
        return -(float) (Math.cos(Math.PI * t) - 1.0) / 2f;
    }

    /** Decelerating with a small overshoot before settling. Good for things arriving. */
    public static float backOut(float t) {
        t = clamp01(t);
        float overshoot = 1.2f;
        float f = t - 1f;
        return 1f + (overshoot + 1f) * f * f * f + overshoot * f * f;
    }

    /** Accelerate then decelerate. */
    public static float quadInOut(float t) {
        t = clamp01(t);
        return (t < 0.5f) ? 2f * t * t : 1f - (float) Math.pow(-2.0 * t + 2.0, 2.0) / 2f;
    }
}
