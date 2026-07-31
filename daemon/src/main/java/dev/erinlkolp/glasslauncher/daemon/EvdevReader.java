package dev.erinlkolp.glasslauncher.daemon;

import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

/**
 * Assembles evdev multitouch protocol A frames into {@link TouchSample}s.
 *
 * <p>Contacts are delimited by {@code SYN_MT_REPORT}; a frame ends at
 * {@code SYN_REPORT}. Coordinates arrive in touchpad-native units and are
 * scaled into screen space so that samples are indistinguishable from those
 * the in-app MotionEvent path produces, letting both share one detector.
 */
public final class EvdevReader {

    private static final float SCREEN_WIDTH = 640.0f;
    private static final float SCREEN_HEIGHT = 360.0f;

    private int contactsInFrame;
    private boolean sawContactFields;
    private int firstX;
    private int firstY;
    private boolean contactActive;
    private long frameTimeMs;

    /** @return a sample when a frame completes, otherwise null. */
    public TouchSample feed(InputEvent event) {
        if (event.type == InputEvent.EV_ABS) {
            switch (event.code) {
                case InputEvent.ABS_MT_POSITION_X:
                    if (contactsInFrame == 0) {
                        firstX = event.value;
                    }
                    sawContactFields = true;
                    break;
                case InputEvent.ABS_MT_POSITION_Y:
                    if (contactsInFrame == 0) {
                        firstY = event.value;
                    }
                    sawContactFields = true;
                    break;
                case InputEvent.ABS_MT_TRACKING_ID:
                    sawContactFields = true;
                    break;
                default:
                    break;
            }
            return null;
        }

        if (event.type != InputEvent.EV_SYN) {
            return null;
        }

        if (event.code == InputEvent.SYN_MT_REPORT) {
            if (sawContactFields) {
                contactsInFrame++;
                sawContactFields = false;
            }
            return null;
        }

        if (event.code != InputEvent.SYN_REPORT) {
            return null;
        }

        frameTimeMs = event.timeMs;
        TouchSample sample = buildSample();
        contactsInFrame = 0;
        sawContactFields = false;
        return sample;
    }

    private TouchSample buildSample() {
        float screenX = firstX * (SCREEN_WIDTH / TouchpadGeometry.NATIVE_WIDTH);
        float screenY = firstY * (SCREEN_HEIGHT / TouchpadGeometry.NATIVE_HEIGHT);

        if (contactsInFrame == 0) {
            if (!contactActive) {
                return null;
            }
            contactActive = false;
            return new TouchSample(TouchPhase.UP, screenX, screenY, frameTimeMs, 0);
        }

        TouchPhase phase = contactActive ? TouchPhase.MOVE : TouchPhase.DOWN;
        contactActive = true;
        return new TouchSample(phase, screenX, screenY, frameTimeMs, contactsInFrame);
    }
}
