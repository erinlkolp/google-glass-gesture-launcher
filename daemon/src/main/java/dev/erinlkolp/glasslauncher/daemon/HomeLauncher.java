package dev.erinlkolp.glasslauncher.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Sends the device to its home screen. Verified working from the shell. */
public final class HomeLauncher {

    private static final String[] COMMAND = {
            "am", "start",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME"
    };

    public void goHome() {
        try {
            ProcessBuilder builder = new ProcessBuilder(COMMAND);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            // Drain the child's output before waiting. Leaving it unread risks
            // filling the OS pipe buffer, at which point `am` blocks writing and
            // waitFor() never returns — permanently wedging this daemon's single
            // read loop and silently disabling the gesture until reboot.
            InputStream out = process.getInputStream();
            byte[] sink = new byte[256];
            while (out.read(sink) != -1) {
                // discard
            }

            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                process.destroy();
                System.err.println("gestured: am start timed out");
            }
        } catch (IOException e) {
            System.err.println("gestured: could not launch home: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
