package dev.erinlkolp.glasslauncher.gesture;

/**
 * One input sample, in screen-space coordinates. Deliberately free of any
 * Android type so that both the in-app MotionEvent path and the root daemon's
 * evdev path can produce it.
 */
public final class TouchSample {

    public final TouchPhase phase;
    public final float x;
    public final float y;
    public final long timeMs;
    public final int pointerCount;

    public TouchSample(TouchPhase phase, float x, float y, long timeMs, int pointerCount) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        this.phase = phase;
        this.x = x;
        this.y = y;
        this.timeMs = timeMs;
        this.pointerCount = pointerCount;
    }
}
