package dev.erinlkolp.glasslauncher.gesture;

/**
 * Recognises Glass touchpad gestures from a stream of {@link TouchSample}s.
 *
 * <p>All measurement happens in touchpad-native units, never in screen pixels;
 * see {@link TouchpadGeometry} for why that distinction is load-bearing.
 *
 * <p>Not thread-safe. Each host owns one instance and feeds it from a single
 * thread.
 */
public final class GlassGestureDetector {

    /**
     * Native units of travel below which a contact is stationary.
     *
     * <p>Deliberately tight. The pad is 1366 units wide but only 187 tall, so a
     * generous slop that is negligible horizontally consumes a large fraction of
     * the vertical range and swallows genuine downward swipes.
     */
    private static final float TAP_SLOP = 25.0f;
    /** Contacts longer than this are too slow to be a tap. */
    private static final long TAP_MAX_MS = 250L;
    /** Native units of horizontal travel required to call a swipe horizontal. */
    private static final float HORIZONTAL_THRESHOLD = 150.0f;
    /**
     * Native units of vertical travel required to call a swipe vertical.
     *
     * <p>Deliberately well below the ~63-unit minimum observed in real captured
     * downward swipes. The pad is only 187 units tall and fingers usually land
     * mid-pad, so the usable downward range is often ~80 units — a threshold
     * near that floor makes the gesture feel intermittent.
     */
    private static final float VERTICAL_THRESHOLD = 45.0f;
    /** Contact duration at or above which a stationary contact is a long press. */
    private static final long LONG_PRESS_MS = 500L;

    private final TouchpadGeometry geometry;
    private final GestureOrientation orientation;

    private boolean tracking;
    private float startNativeX;
    private float startNativeY;
    private long startTimeMs;
    private int maxPointerCount;

    public GlassGestureDetector(TouchpadGeometry geometry, GestureOrientation orientation) {
        if (geometry == null || orientation == null) {
            throw new IllegalArgumentException("geometry and orientation must not be null");
        }
        this.geometry = geometry;
        this.orientation = orientation;
    }

    /**
     * @return the recognised gesture, or {@link Gesture#NONE} if this sample did
     *         not complete one.
     */
    public Gesture accept(TouchSample sample) {
        switch (sample.phase) {
            case DOWN:
                tracking = true;
                startNativeX = geometry.toNativeX(sample.x);
                startNativeY = geometry.toNativeY(sample.y);
                startTimeMs = sample.timeMs;
                maxPointerCount = sample.pointerCount;
                return Gesture.NONE;

            case MOVE:
                if (tracking && sample.pointerCount > maxPointerCount) {
                    maxPointerCount = sample.pointerCount;
                }
                return Gesture.NONE;

            case CANCEL:
                tracking = false;
                return Gesture.NONE;

            case UP:
                if (!tracking) {
                    return Gesture.NONE;
                }
                tracking = false;
                if (sample.pointerCount > maxPointerCount) {
                    maxPointerCount = sample.pointerCount;
                }
                return classify(sample);

            default:
                return Gesture.NONE;
        }
    }

    private Gesture classify(TouchSample up) {
        float dx = geometry.toNativeX(up.x) - startNativeX;
        float dy = geometry.toNativeY(up.y) - startNativeY;
        if (orientation.invertX) {
            dx = -dx;
        }
        if (orientation.invertY) {
            dy = -dy;
        }

        long durationMs = up.timeMs - startTimeMs;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean multiTouch = maxPointerCount >= 2;

        if (distance < TAP_SLOP) {
            if (multiTouch) {
                // Two-finger taps carry no meaning in this design.
                return Gesture.NONE;
            }
            if (durationMs >= LONG_PRESS_MS) {
                return Gesture.LONG_PRESS;
            }
            if (durationMs <= TAP_MAX_MS) {
                return Gesture.TAP;
            }
            // Between the two: too slow to be a tap, too quick to be a long
            // press. Ambiguous input must be a no-op, never an app launch.
            return Gesture.NONE;
        }

        // Both deltas are in the same physical units, so a bare magnitude
        // comparison is now meaningful. In screen space it would not be.
        boolean horizontal = Math.abs(dx) >= Math.abs(dy);

        if (horizontal) {
            if (Math.abs(dx) < HORIZONTAL_THRESHOLD) {
                return Gesture.NONE;
            }
            if (multiTouch) {
                return dx > 0.0f
                        ? Gesture.TWO_FINGER_SWIPE_FORWARD
                        : Gesture.TWO_FINGER_SWIPE_BACKWARD;
            }
            return dx > 0.0f ? Gesture.SWIPE_FORWARD : Gesture.SWIPE_BACKWARD;
        }

        if (Math.abs(dy) < VERTICAL_THRESHOLD) {
            return Gesture.NONE;
        }
        // Upward swipes are unassigned; only downward is meaningful.
        if (dy <= 0.0f) {
            return Gesture.NONE;
        }
        return multiTouch ? Gesture.TWO_FINGER_SWIPE_DOWN : Gesture.SWIPE_DOWN;
    }
}
