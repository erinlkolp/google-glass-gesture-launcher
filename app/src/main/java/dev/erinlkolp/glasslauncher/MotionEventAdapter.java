package dev.erinlkolp.glasslauncher;

import android.view.MotionEvent;
import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;

/** Translates Android {@link MotionEvent}s into device-agnostic samples. */
public final class MotionEventAdapter {

    private MotionEventAdapter() {
    }

    /** @return a sample, or null if this event carries no useful phase. */
    public static TouchSample toSample(MotionEvent event) {
        TouchPhase phase;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                phase = TouchPhase.DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                phase = TouchPhase.MOVE;
                break;
            case MotionEvent.ACTION_UP:
                phase = TouchPhase.UP;
                break;
            case MotionEvent.ACTION_CANCEL:
                phase = TouchPhase.CANCEL;
                break;
            default:
                return null;
        }
        return new TouchSample(
                phase,
                event.getX(),
                event.getY(),
                event.getEventTime(),
                event.getPointerCount());
    }
}
