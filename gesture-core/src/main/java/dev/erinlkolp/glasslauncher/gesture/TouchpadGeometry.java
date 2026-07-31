package dev.erinlkolp.glasslauncher.gesture;

/**
 * Converts screen-space coordinates back into the touchpad's native units.
 *
 * <p>The Glass touchpad reports a native surface of 1366 x 187. Android's
 * InputReader linearly rescales that onto the 640 x 360 display, which is a
 * different aspect ratio, so the coordinates an application receives are
 * anisotropic: vertical motion is amplified roughly 4.11x relative to
 * horizontal. Doing distance or angle arithmetic in screen space therefore
 * produces physically wrong answers. Every measurement in this package is
 * performed in native units obtained through this class.
 */
public final class TouchpadGeometry {

    public static final int NATIVE_WIDTH = 1366;
    public static final int NATIVE_HEIGHT = 187;

    /** The measured configuration of the Glass unit this project targets. */
    public static final TouchpadGeometry GLASS = new TouchpadGeometry(640.0f, 360.0f);

    private final float xScale;
    private final float yScale;

    public TouchpadGeometry(float screenWidth, float screenHeight) {
        if (screenWidth <= 0.0f || screenHeight <= 0.0f) {
            throw new IllegalArgumentException(
                    "screen dimensions must be positive, got "
                            + screenWidth + "x" + screenHeight);
        }
        this.xScale = NATIVE_WIDTH / screenWidth;
        this.yScale = NATIVE_HEIGHT / screenHeight;
    }

    public float toNativeX(float screenX) {
        return screenX * xScale;
    }

    public float toNativeY(float screenY) {
        return screenY * yScale;
    }
}
