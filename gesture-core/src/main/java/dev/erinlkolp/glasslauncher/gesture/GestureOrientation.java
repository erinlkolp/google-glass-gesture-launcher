package dev.erinlkolp.glasslauncher.gesture;

/**
 * Which physical direction each touchpad axis increases in.
 *
 * <p>Spec section 7.2 left both axis directions unknown. Determined empirically on
 * 2026-07-30 by capturing raw evdev events from {@code sensor00fn11} during
 * deliberate single-finger swipes on the physical device:
 *
 * <ul>
 *   <li>A <em>forward</em> swipe (ear toward display) drove
 *       {@code ABS_MT_POSITION_X} from 11 to 1366 — X <b>increases</b> forward.</li>
 *   <li>A <em>downward</em> swipe drove {@code ABS_MT_POSITION_Y} from 0 to 187 —
 *       Y <b>increases</b> downward.</li>
 * </ul>
 *
 * <p>Both therefore match the detector's assumed sense, and {@link #DEFAULT}
 * needs no inversion on either axis. Raw capture retained at
 * {@code .superpowers/sdd/2026-07-30-glass-gesture-launcher/orientation-capture.txt}.
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
