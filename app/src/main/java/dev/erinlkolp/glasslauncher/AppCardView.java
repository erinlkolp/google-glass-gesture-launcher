package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Draws the currently selected app entry, one at a time. */
public final class AppCardView extends View {

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<AppEntry> entries = new ArrayList<AppEntry>();
    private int selectedIndex;
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

    public void setEntries(List<AppEntry> entries) {
        boolean sameList = entries.size() == this.entries.size();
        if (sameList) {
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.get(i).activityName.equals(this.entries.get(i).activityName)) {
                    sameList = false;
                    break;
                }
            }
        }
        this.entries = entries;
        if (!sameList) {
            this.selectedIndex = 0;
        } else if (this.selectedIndex >= entries.size()) {
            this.selectedIndex = Math.max(0, entries.size() - 1);
        }
        invalidate();
    }

    public AppEntry selected() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(selectedIndex);
    }

    /** Moves the selection by {@code delta}, clamped to the list bounds. */
    public void move(int delta) {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(entries.size() - 1, selectedIndex + delta));
        invalidate();
    }

    public void recenter() {
        selectedIndex = 0;
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

        if (entries.isEmpty()) {
            canvas.drawText("No launchable apps", centerX, centerY, labelPaint);
            return;
        }

        AppEntry entry = entries.get(selectedIndex);
        canvas.drawText(entry.label, centerX, centerY, labelPaint);
        canvas.drawText((selectedIndex + 1) + " / " + entries.size(),
                centerX, getHeight() - 20.0f, counterPaint);

        if (showingDetail) {
            canvas.drawText(entry.packageName, centerX, centerY + 34.0f, detailPaint);
            canvas.drawText(entry.activityName, centerX, centerY + 56.0f, detailPaint);
        }
    }
}
