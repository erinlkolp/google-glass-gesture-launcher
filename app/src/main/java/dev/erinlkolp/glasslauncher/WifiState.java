package dev.erinlkolp.glasslauncher;

import android.net.wifi.WifiManager;

/**
 * Wi-Fi radio state and the text shown for it.
 *
 * <p>Takes a raw {@code int} rather than a {@link WifiManager} so the mapping and the
 * labels stay unit-testable on the JVM. The {@code WIFI_STATE_*} constants referenced
 * below are compile-time constants and are inlined by javac, so naming them here costs
 * no runtime dependency on the framework.
 */
public enum WifiState {

    // The ellipsis is a \u2026 escape rather than a literal character: the build sets
    // file.encoding through jvmargs but never passes -encoding to javac explicitly, so
    // a literal would be at the mercy of the platform default.
    DISABLING("Turning off\u2026"),
    OFF("Off"),
    ENABLING("Turning on\u2026"),
    ON("On"),
    UNKNOWN("Unknown"),
    /** No {@code WifiManager} at all — the device has no Wi-Fi service. */
    UNAVAILABLE("Unavailable");

    private final String label;

    WifiState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static WifiState fromCode(int code) {
        switch (code) {
            case WifiManager.WIFI_STATE_DISABLING:
                return DISABLING;
            case WifiManager.WIFI_STATE_DISABLED:
                return OFF;
            case WifiManager.WIFI_STATE_ENABLING:
                return ENABLING;
            case WifiManager.WIFI_STATE_ENABLED:
                return ON;
            default:
                return UNKNOWN;
        }
    }
}
