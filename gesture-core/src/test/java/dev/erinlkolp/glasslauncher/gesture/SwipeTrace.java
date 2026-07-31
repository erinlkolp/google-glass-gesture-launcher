package dev.erinlkolp.glasslauncher.gesture;

import java.util.ArrayList;
import java.util.List;

/** Builds synthetic touch traces in screen-space pixels for detector tests. */
final class SwipeTrace {

    private final List<TouchSample> samples = new ArrayList<TouchSample>();

    static List<TouchSample> straight(float fromX, float fromY,
                                      float toX, float toY,
                                      long durationMs, int pointerCount) {
        SwipeTrace trace = new SwipeTrace();
        int steps = 10;
        trace.samples.add(new TouchSample(TouchPhase.DOWN, fromX, fromY, 0L, pointerCount));
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            trace.samples.add(new TouchSample(
                    TouchPhase.MOVE,
                    fromX + (toX - fromX) * t,
                    fromY + (toY - fromY) * t,
                    (long) (durationMs * t),
                    pointerCount));
        }
        trace.samples.add(new TouchSample(TouchPhase.UP, toX, toY, durationMs, pointerCount));
        return trace.samples;
    }

    static Gesture play(GlassGestureDetector detector, List<TouchSample> samples) {
        Gesture last = Gesture.NONE;
        for (TouchSample sample : samples) {
            Gesture g = detector.accept(sample);
            if (g != Gesture.NONE) {
                last = g;
            }
        }
        return last;
    }
}
