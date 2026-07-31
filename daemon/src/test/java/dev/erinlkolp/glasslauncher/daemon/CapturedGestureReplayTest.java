package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Replays real captures from the device through the full daemon pipeline:
 * InputEvent.parse -> EvdevReader -> GlassGestureDetector.
 *
 * <p>These fixtures were recorded from physical swipes on the hardware. Without
 * this test they are documentation; with it they are a regression guard against
 * threshold drift and adapter divergence.
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

    @Test
    public void capturedTwoFingerSwipesAreRecognisedAsTheHomeGesture() throws IOException {
        List<Gesture> gestures = replay("/two-finger-down.getevent.txt");
        assertTrue("expected at least one gesture from the capture, got " + gestures,
                !gestures.isEmpty());
        assertTrue("expected TWO_FINGER_SWIPE_DOWN in " + gestures,
                gestures.contains(Gesture.TWO_FINGER_SWIPE_DOWN));
    }

    @Test
    public void capturedSingleFingerSwipesNeverTriggerTheHomeGesture() throws IOException {
        List<Gesture> gestures = replay("/single-finger-swipes.getevent.txt");
        assertTrue("expected at least one gesture from the capture, got " + gestures,
                !gestures.isEmpty());
        assertEquals("single-finger capture must not produce the global home gesture",
                false, gestures.contains(Gesture.TWO_FINGER_SWIPE_DOWN));
    }

    @Test
    public void capturedSingleFingerCaptureContainsAForwardSwipe() throws IOException {
        assertTrue(replay("/single-finger-swipes.getevent.txt")
                .contains(Gesture.SWIPE_FORWARD));
    }
}
