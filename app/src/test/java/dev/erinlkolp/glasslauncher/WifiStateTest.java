package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.net.wifi.WifiManager;
import org.junit.Test;

public class WifiStateTest {

    @Test
    public void mapsEveryDocumentedCode() {
        assertEquals(WifiState.DISABLING, WifiState.fromCode(WifiManager.WIFI_STATE_DISABLING));
        assertEquals(WifiState.OFF, WifiState.fromCode(WifiManager.WIFI_STATE_DISABLED));
        assertEquals(WifiState.ENABLING, WifiState.fromCode(WifiManager.WIFI_STATE_ENABLING));
        assertEquals(WifiState.ON, WifiState.fromCode(WifiManager.WIFI_STATE_ENABLED));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(WifiManager.WIFI_STATE_UNKNOWN));
    }

    @Test
    public void mapsUnrecognisedCodesToUnknown() {
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(99));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(-1));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(Integer.MIN_VALUE));
    }

    @Test
    public void labelsAreShortEnoughForTheDisplay() {
        assertEquals("On", WifiState.ON.label());
        assertEquals("Off", WifiState.OFF.label());
        assertEquals("Turning on\u2026", WifiState.ENABLING.label());
        assertEquals("Turning off\u2026", WifiState.DISABLING.label());
        assertEquals("Unknown", WifiState.UNKNOWN.label());
        assertEquals("Unavailable", WifiState.UNAVAILABLE.label());
    }

    @Test
    public void everyStateHasANonEmptyLabel() {
        for (WifiState state : WifiState.values()) {
            assertTrue(state.name(), state.label().length() > 0);
        }
    }
}
