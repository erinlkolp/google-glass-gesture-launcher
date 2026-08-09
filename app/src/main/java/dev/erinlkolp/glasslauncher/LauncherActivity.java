package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.util.Collections;
import java.util.List;

public class LauncherActivity extends Activity {

    /** Entries skipped by a two-finger horizontal swipe. */
    private static final int PAGE_JUMP = 10;

    private GlassGestureDetector detector;
    private AppCardView cardView;
    private AppRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        repository = new AppRepository(this);
        repository.start();
        cardView = new AppCardView(this);
        refreshTiles();
        setContentView(cardView);
        applyImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        repository.stop();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    /**
     * Keeps edge swipes with this activity instead of the system.
     *
     * <p>The StatusBar window claims the top 38 px of the display as touchable.
     * The touchpad's 187 vertical units are mapped onto 360 px, so the top ~20
     * units of the strip land in that region and downward swipes were being
     * routed to the notification shade. IMMERSIVE_STICKY suppresses that.
     */
    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTiles();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        TouchSample sample = MotionEventAdapter.toSample(event);
        if (sample == null) {
            return super.onTouchEvent(event);
        }
        handle(detector.accept(sample));
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            cardView.recenter();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handle(Gesture gesture) {
        switch (gesture) {
            case SWIPE_FORWARD:
                cardView.move(1);
                break;
            case SWIPE_BACKWARD:
                cardView.move(-1);
                break;
            case TWO_FINGER_SWIPE_FORWARD:
                cardView.move(PAGE_JUMP);
                break;
            case TWO_FINGER_SWIPE_BACKWARD:
                cardView.move(-PAGE_JUMP);
                break;
            case TAP:
                activateSelected();
                break;
            case LONG_PRESS:
                cardView.setShowingDetail(!cardView.isShowingDetail());
                break;
            case SWIPE_DOWN:
                // Dismiss the detail view if it is open. At top level, do nothing:
                // this activity is the home screen, so finish() would simply cause
                // ActivityManager to relaunch it.
                if (cardView.isShowingDetail()) {
                    cardView.setShowingDetail(false);
                }
                break;
            case TWO_FINGER_SWIPE_DOWN:
                // Handled globally by the root daemon; see spec section 5.
                break;
            default:
                break;
        }
    }

    /** The tiles pinned ahead of the app list. */
    private List<Tile> pinnedTiles() {
        return Collections.emptyList();
    }

    /**
     * Rebuilds the card list. Cheap: {@link AppRepository#load()} returns its cache
     * unless a package actually changed, and {@link TileSelection} keeps the selection
     * when the rebuilt list is identical.
     */
    private void refreshTiles() {
        cardView.setEntries(TileListBuilder.build(pinnedTiles(), repository.load()));
    }

    private void activateSelected() {
        Tile tile = cardView.selected();
        if (tile == null) {
            return;
        }
        tile.activate(this);
    }
}
