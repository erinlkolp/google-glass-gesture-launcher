package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Replays real captures from the device through the full daemon pipeline:
 * InputEvent.parse -> EvdevReader -> GlassGestureDetector.
 *
 * <p>These fixtures were recorded from physical swipes on the hardware. Without
 * this test they are documentation; with it they are a regression guard against
 * pipeline behaviour: frame assembly, pointer counting, and adapter divergence.
 *
 * <p>Note this fixture does not discriminate {@code VERTICAL_THRESHOLD = 45} from
 * the old {@code 60}: the multi-touch strokes here measure 63/134/121 native units
 * of vertical travel, clearing both values comfortably. It only starts failing
 * around a threshold of roughly 135 or higher. Treat these tests as a guard on
 * pipeline behaviour, not on the specific threshold constant.
 */
public class CapturedGestureReplayTest {

    private static List<Gesture> replay(String resource) throws IOException {
        EvdevReader reader = new EvdevReader();
        GlassGestureDetector detector =
                new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        List<Gesture> recognised = new ArrayList<Gesture>();
        for (InputEvent event : GeteventFixture.load(resource)) {
            TouchSample sample = reader.feed(event);
            if (sample == null) {
                continue;
            }
            Gesture g = detector.accept(sample);
            if (g != Gesture.NONE) {
                recognised.add(g);
            }
        }
        return recognised;
    }

    /**
     * Asserts the EXACT gesture sequence the capture produces, not merely that
     * the home gesture appears somewhere in it. Any change to the pipeline —
     * frame assembly, thresholds, pointer counting — shifts this list.
     *
     * <p>Note the leading single-finger SWIPE_DOWN. That is real hardware
     * behaviour, not a bug: on the first stroke of this capture the second
     * finger lands a beat after the first, so the opening frames genuinely
     * describe a one-finger downward swipe. It is harmless in practice because
     * SWIPE_DOWN is a no-op at the launcher's top level.
     *
     * <p>The trailing TWO_FINGER_SWIPE_FORWARD (dx ~390 native units) is the
     * fourth recognised gesture, not the fifth: it is followed by one more
     * TWO_FINGER_SWIPE_DOWN, not preceded by all three. Verified directly
     * against this repository's InputEvent/EvdevReader/GlassGestureDetector.
     */
    @Test
    public void capturedTwoFingerCaptureProducesTheExpectedGestureSequence() throws IOException {
        assertEquals(
                Arrays.asList(
                        Gesture.SWIPE_DOWN,
                        Gesture.TWO_FINGER_SWIPE_DOWN,
                        Gesture.TWO_FINGER_SWIPE_DOWN,
                        Gesture.TWO_FINGER_SWIPE_FORWARD,
                        Gesture.TWO_FINGER_SWIPE_DOWN),
                replay("/two-finger-down.getevent.txt"));
    }

    /**
     * Asserts the EXACT gesture sequence for the single-finger capture. The
     * absence of TWO_FINGER_SWIPE_DOWN anywhere in this list is the point of
     * this fixture: it is the negative control for the home gesture.
     */
    @Test
    public void capturedSingleFingerCaptureProducesTheExpectedGestureSequence() throws IOException {
        assertEquals(
                Arrays.asList(
                        Gesture.SWIPE_FORWARD,
                        Gesture.SWIPE_DOWN),
                replay("/single-finger-swipes.getevent.txt"));
    }
}
