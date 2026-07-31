package dev.erinlkolp.glasslauncher.gesture;

/**
 * Which physical direction each touchpad axis increases in.
 *
 * <p>Spec section 7.2: it is not yet known which end of the temple corresponds
 * to X = 0, nor whether Y increases upward or downward. Task 6 determines both
 * empirically using the on-device debug overlay and updates {@link #DEFAULT}.
 */
public final class GestureOrientation {

    public static final GestureOrientation DEFAULT = new GestureOrientation(false, false);

    public final boolean invertX;
    public final boolean invertY;

    public GestureOrientation(boolean invertX, boolean invertY) {
        this.invertX = invertX;
        this.invertY = invertY;
    }
}
