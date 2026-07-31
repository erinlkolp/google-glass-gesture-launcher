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

    /** Native units of travel below which a contact is stationary. */
    private static final float TAP_SLOP = 40.0f;
    /** Native units of horizontal travel required to call a swipe horizontal. */
    private static final float HORIZONTAL_THRESHOLD = 150.0f;
    /** Native units of vertical travel required to call a swipe vertical. */
    private static final float VERTICAL_THRESHOLD = 60.0f;
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

        if (distance < TAP_SLOP) {
            return durationMs >= LONG_PRESS_MS ? Gesture.LONG_PRESS : Gesture.TAP;
        }

        // Both deltas are in the same physical units, so a bare magnitude
        // comparison is now meaningful. In screen space it would not be.
        boolean horizontal = Math.abs(dx) >= Math.abs(dy);

        if (horizontal) {
            if (Math.abs(dx) < HORIZONTAL_THRESHOLD) {
                return Gesture.NONE;
            }
            return dx > 0.0f ? Gesture.SWIPE_FORWARD : Gesture.SWIPE_BACKWARD;
        }

        if (Math.abs(dy) < VERTICAL_THRESHOLD) {
            return Gesture.NONE;
        }
        // Upward swipes are unassigned; only downward is meaningful.
        return dy > 0.0f ? Gesture.SWIPE_DOWN : Gesture.NONE;
    }
}
