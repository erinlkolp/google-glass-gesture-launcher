package dev.erinlkolp.glasslauncher.daemon;

import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Watches the touchpad for a global two-finger downward swipe and sends the
 * device home when it sees one.
 *
 * <p>Runs as root via {@code app_process}, because an application uid can
 * neither execute {@code /system/xbin/su} (mode 4750, group shell) nor open
 * {@code /dev/input/event3} (mode 0660, group input). See spec section 4.3.
 *
 * <p>The device is opened for reading only and is never grabbed with
 * {@code EVIOCGRAB}; an exclusive grab would block input system-wide.
 */
public final class Main {

    private static final String DEVICE_NAME = "sensor00fn11";

    public static void main(String[] args) throws InterruptedException {
        String node;
        try {
            node = locateTouchpad();
        } catch (IOException e) {
            System.err.println("gestured: could not find input device " + DEVICE_NAME);
            System.exit(1);
            return;
        }
        if (node == null) {
            System.err.println("gestured: could not find input device " + DEVICE_NAME);
            System.exit(1);
            return;
        }
        System.out.println("gestured: watching " + node);

        while (true) {
            try {
                watch(node);
                System.err.println("gestured: input stream ended, reopening");
            } catch (IOException e) {
                System.err.println("gestured: read error, reopening: " + e.getMessage());
            }
            Thread.sleep(2000L);
        }
    }

    private static void watch(String node) throws IOException {
        EvdevReader reader = new EvdevReader();
        GlassGestureDetector detector =
                new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        HomeLauncher home = new HomeLauncher();

        InputStream in = new FileInputStream(node);
        byte[] buffer = new byte[InputEvent.SIZE_BYTES * 64];
        try {
            while (true) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                for (int offset = 0;
                     offset + InputEvent.SIZE_BYTES <= read;
                     offset += InputEvent.SIZE_BYTES) {
                    TouchSample sample = reader.feed(InputEvent.parse(buffer, offset));
                    if (sample == null) {
                        continue;
                    }
                    if (detector.accept(sample) == Gesture.TWO_FINGER_SWIPE_DOWN) {
                        System.out.println("gestured: two-finger down -> home");
                        home.goHome();
                    }
                }
            }
        } finally {
            in.close();
        }
    }

    private static String locateTouchpad() throws IOException {
        InputStream in = new FileInputStream("/proc/bus/input/devices");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return EvdevDeviceLocator.findByName(out.toString("UTF-8"), DEVICE_NAME);
    }
}
