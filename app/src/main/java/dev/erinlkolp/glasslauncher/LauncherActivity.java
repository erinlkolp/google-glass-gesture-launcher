package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        TextView placeholder = new TextView(this);
        placeholder.setBackgroundColor(Color.BLACK);
        placeholder.setTextColor(Color.WHITE);
        placeholder.setTextSize(24.0f);
        placeholder.setText("Glass Launcher\n"
                + metrics.widthPixels + " x " + metrics.heightPixels
                + " @ " + metrics.densityDpi + "dpi");
        placeholder.setPadding(16, 16, 16, 16);
        setContentView(placeholder);

        placeholder.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }
}
