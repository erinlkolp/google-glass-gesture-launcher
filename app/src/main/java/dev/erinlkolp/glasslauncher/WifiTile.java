package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;

/**
 * A tile that reports and toggles the Wi-Fi radio.
 *
 * <p>{@code setWifiEnabled} is usable directly because this device is API 22; the
 * restriction that forces apps out to Settings landed in API 29.
 *
 * <p>State is read fresh on every {@link #label()} call rather than cached, so the
 * activity only has to call {@code invalidate()} when the state-changed broadcast
 * arrives.
 */
public final class WifiTile implements Tile {

    private static final String KEY = "action:wifi";

    private final WifiManager wifiManager;

    public WifiTile(Context context) {
        this.wifiManager = (WifiManager)
                context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private WifiState state() {
        if (wifiManager == null) {
            return WifiState.UNAVAILABLE;
        }
        try {
            return WifiState.fromCode(wifiManager.getWifiState());
        } catch (Exception e) {
            return WifiState.UNKNOWN;
        }
    }

    @Override
    public String label() {
        return "Wi-Fi: " + state().label();
    }

    @Override
    public List<String> detailLines() {
        return Arrays.asList("Toggles the Wi-Fi radio", "State: " + state().label());
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public void activate(Context context) {
        WifiState current = state();
        // Ignore taps mid-transition so an impatient double tap cannot queue an
        // enable immediately followed by a disable.
        if (current != WifiState.ON && current != WifiState.OFF) {
            return;
        }
        boolean enable = current == WifiState.OFF;
        try {
            if (!wifiManager.setWifiEnabled(enable)) {
                toastFailure(context);
            }
        } catch (Exception e) {
            toastFailure(context);
        }
    }

    private static void toastFailure(Context context) {
        Toast.makeText(context, "Could not toggle Wi-Fi", Toast.LENGTH_SHORT).show();
    }
}
