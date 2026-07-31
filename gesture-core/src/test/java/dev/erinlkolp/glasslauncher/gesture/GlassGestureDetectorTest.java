package dev.erinlkolp.glasslauncher.gesture;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GlassGestureDetectorTest {

    private GlassGestureDetector detector;

    @Before
    public void setUp() {
        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
    }

    @Test
    public void shortStationaryContactIsATap() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 305f, 182f, 120L, 1);
        assertEquals(Gesture.TAP, SwipeTrace.play(detector, trace));
    }

    @Test
    public void longStationaryContactIsALongPress() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 303f, 181f, 700L, 1);
        assertEquals(Gesture.LONG_PRESS, SwipeTrace.play(detector, trace));
    }

    @Test
    public void clearHorizontalTravelIsAForwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 1);
        assertEquals(Gesture.SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    /**
     * THE critical test. This swipe travels 117 px horizontally and 135 px
     * vertically in SCREEN space, so a naive detector comparing raw pixel
     * deltas concludes "vertical" and reports SWIPE_DOWN.
     *
     * Converted to native units it is 249.7 x 70.1 - overwhelmingly horizontal.
     * The physical finger motion was a forward swipe with slight downward drift.
     * If this test fails, the anisotropy correction is not being applied.
     */
    @Test
    public void screenSpaceVerticalDominanceDoesNotOverrideNativeHorizontalDominance() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 20f, 217f, 155f, 200L, 1);
        assertEquals(Gesture.SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void negativeHorizontalTravelIsABackwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(400f, 180f, 200f, 190f, 200L, 1);
        assertEquals(Gesture.SWIPE_BACKWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void genuineVerticalTravelIsADownSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 40f, 310f, 300f, 200L, 1);
        assertEquals(Gesture.SWIPE_DOWN, SwipeTrace.play(detector, trace));
    }

    @Test
    public void upwardTravelIsNotRecognised() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 300f, 310f, 40f, 200L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    @Test
    public void invertedOrientationFlipsForwardAndBackward() {
        GlassGestureDetector inverted = new GlassGestureDetector(
                TouchpadGeometry.GLASS, new GestureOrientation(true, false));
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 1);
        assertEquals(Gesture.SWIPE_BACKWARD, SwipeTrace.play(inverted, trace));
    }

    @Test
    public void cancelledContactProducesNothing() {
        detector.accept(new TouchSample(TouchPhase.DOWN, 100f, 180f, 0L, 1));
        detector.accept(new TouchSample(TouchPhase.MOVE, 300f, 180f, 100L, 1));
        assertEquals(Gesture.NONE,
                detector.accept(new TouchSample(TouchPhase.CANCEL, 300f, 180f, 150L, 1)));
    }

    @Test
    public void twoFingerHorizontalTravelIsATwoFingerForwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void twoFingerBackwardTravelIsATwoFingerBackwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(400f, 180f, 200f, 190f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_BACKWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void twoFingerDownwardTravelIsTheGlobalHomeGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 40f, 310f, 300f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_DOWN, SwipeTrace.play(detector, trace));
    }

    /**
     * A second finger landing mid-gesture must still count. The peak pointer
     * count across the whole contact decides, not the count at lift-off.
     */
    @Test
    public void secondFingerArrivingMidSwipeStillCountsAsTwoFinger() {
        detector.accept(new TouchSample(TouchPhase.DOWN, 300f, 40f, 0L, 1));
        detector.accept(new TouchSample(TouchPhase.MOVE, 305f, 150f, 100L, 2));
        assertEquals(Gesture.TWO_FINGER_SWIPE_DOWN,
                detector.accept(new TouchSample(TouchPhase.UP, 310f, 300f, 200L, 1)));
    }

    @Test
    public void twoFingerStationaryContactIsNotAGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 303f, 181f, 120L, 2);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    /**
     * A contact too slow to be a tap but too quick to be a long press must be
     * NOTHING. Previously this returned TAP, which launched the selected app —
     * the most destructive possible outcome for a misread gesture.
     */
    @Test
    public void contactBetweenTapAndLongPressDurationsIsNotAGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 304f, 181f, 350L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    /**
     * A deliberate but insufficient downward drag must be NOTHING — not a tap,
     * and not a swipe. 60 screen px of vertical travel is ~31.2 native units:
     * past TAP_SLOP (25) so it is not stationary, but short of
     * VERTICAL_THRESHOLD (60) so it is not a swipe either.
     *
     * <p>This is the gap that previously launched apps: an aborted downward
     * swipe fell under the old TAP_SLOP of 40 and read as a tap.
     */
    @Test
    public void insufficientDownwardDragIsNeitherTapNorSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 100f, 302f, 160f, 200L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    @Test
    public void quickStationaryContactIsStillATap() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 302f, 181f, 90L, 1);
        assertEquals(Gesture.TAP, SwipeTrace.play(detector, trace));
    }
}
