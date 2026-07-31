package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class EvdevDeviceLocatorTest {

    private static String fixture() throws IOException {
        InputStream in = EvdevDeviceLocatorTest.class
                .getResourceAsStream("/proc-bus-input-devices.txt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
        in.close();
        return out.toString("UTF-8");
    }

    @Test
    public void findsTheTouchpadInRealDeviceOutput() throws IOException {
        assertEquals("/dev/input/event3",
                EvdevDeviceLocator.findByName(fixture(), "sensor00fn11"));
    }

    @Test
    public void returnsNullForAnUnknownDevice() throws IOException {
        assertNull(EvdevDeviceLocator.findByName(fixture(), "no-such-device"));
    }

    @Test
    public void findsADeviceOtherThanTheTouchpad() throws IOException {
        assertEquals("/dev/input/event5",
                EvdevDeviceLocator.findByName(fixture(), "gpio-keys"));
    }
}
