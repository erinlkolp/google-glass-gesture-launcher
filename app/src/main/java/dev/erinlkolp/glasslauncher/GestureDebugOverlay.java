package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

/**
 * Live touchpad diagnostics. Exists to make the touchpad observable rather than
 * guessed at, and specifically to answer which physical end of the temple is
 * X = 0 (spec section 7.2).
 */
public final class GestureDebugOverlay extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TouchpadGeometry geometry = TouchpadGeometry.GLASS;

    private String phaseLine = "no input yet";
    private String coordLine = "";
    private String nativeLine = "";
    private String gestureLine = "last gesture: none";

    public GestureDebugOverlay(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        paint.setColor(Color.WHITE);
        paint.setTextSize(18.0f);
    }

    public void record(TouchSample sample, Gesture gesture) {
        if (sample != null) {
            phaseLine = "phase: " + sample.phase + "   pointers: " + sample.pointerCount;
            coordLine = String.format("screen: %.0f, %.0f", sample.x, sample.y);
            nativeLine = String.format("native: %.0f, %.0f",
                    geometry.toNativeX(sample.x), geometry.toNativeY(sample.y));
        }
        if (gesture != null && gesture != Gesture.NONE) {
            gestureLine = "last gesture: " + gesture;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float y = 30.0f;
        canvas.drawText(phaseLine, 12.0f, y, paint);
        canvas.drawText(coordLine, 12.0f, y + 26.0f, paint);
        canvas.drawText(nativeLine, 12.0f, y + 52.0f, paint);
        canvas.drawText(gestureLine, 12.0f, y + 78.0f, paint);
    }
}
