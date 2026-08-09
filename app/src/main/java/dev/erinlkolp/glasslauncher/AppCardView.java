package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.List;

/** Draws the currently selected tile, one at a time. */
public final class AppCardView extends View {

    /** Vertical gap between successive detail lines. */
    private static final float DETAIL_LINE_HEIGHT = 22.0f;

    /** Offset of the first detail line below the label baseline. */
    private static final float DETAIL_TOP_OFFSET = 34.0f;

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TileSelection selection = new TileSelection();
    private boolean showingDetail;

    public AppCardView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(34.0f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        detailPaint.setColor(Color.WHITE);
        detailPaint.setTextSize(16.0f);
        detailPaint.setTextAlign(Paint.Align.CENTER);

        counterPaint.setColor(Color.WHITE);
        counterPaint.setTextSize(16.0f);
        counterPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEntries(List<Tile> tiles) {
        selection.setTiles(tiles);
        invalidate();
    }

    /** @return the selected tile, or null when there is nothing to show. */
    public Tile selected() {
        return selection.selected();
    }

    /** Moves the selection by {@code delta}, clamped to the list bounds. */
    public void move(int delta) {
        selection.move(delta);
        invalidate();
    }

    public void recenter() {
        selection.recenter();
        invalidate();
    }

    public boolean isShowingDetail() {
        return showingDetail;
    }

    public void setShowingDetail(boolean showingDetail) {
        this.showingDetail = showingDetail;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        if (selection.isEmpty()) {
            canvas.drawText("No launchable apps", centerX, centerY, labelPaint);
            return;
        }

        Tile tile = selection.selected();
        // Labels are read at draw time so a stateful tile repaints correctly on
        // nothing more than invalidate().
        canvas.drawText(tile.label(), centerX, centerY, labelPaint);
        canvas.drawText((selection.selectedIndex() + 1) + " / " + selection.size(),
                centerX, getHeight() - 20.0f, counterPaint);

        if (showingDetail) {
            List<String> lines = tile.detailLines();
            for (int i = 0; i < lines.size(); i++) {
                canvas.drawText(lines.get(i), centerX,
                        centerY + DETAIL_TOP_OFFSET + i * DETAIL_LINE_HEIGHT, detailPaint);
            }
        }
    }
}
