package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

public class LauncherActivity extends Activity {

    private GlassGestureDetector detector;
    private GestureDebugOverlay overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        overlay = new GestureDebugOverlay(this);
        setContentView(overlay);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        TouchSample sample = MotionEventAdapter.toSample(event);
        if (sample == null) {
            return super.onTouchEvent(event);
        }
        Gesture gesture = detector.accept(sample);
        overlay.record(sample, gesture);
        return true;
    }
}
