package dev.erinlkolp.glasslauncher.daemon;

import java.io.IOException;
import java.io.InputStream;

/** Sends the device to its home screen. Verified working from the shell. */
public final class HomeLauncher {

    private static final String[] COMMAND = {
            "am", "start",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME"
    };

    /** Upper bound on a single goHome() call, including draining. */
    private static final long TIMEOUT_MS = 5000L;

    public void goHome() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(COMMAND);
            builder.redirectErrorStream(true);
            process = builder.start();

            // Bound the WHOLE call, not just waitFor. Destroying the child closes
            // its stdout, which unblocks the drain loop below. Without this a
            // hung `am` would wedge this daemon's single read loop forever and
            // silently disable the gesture until reboot.
            final Process watched = process;
            Thread watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(TIMEOUT_MS);
                        watched.destroy();
                    } catch (InterruptedException expected) {
                        // Completed in time; nothing to do.
                    }
                }
            }, "gestured-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            InputStream out = process.getInputStream();
            byte[] sink = new byte[256];
            while (out.read(sink) != -1) {
                // discard
            }
            process.waitFor();
            watchdog.interrupt();
        } catch (IOException e) {
            System.err.println("gestured: could not launch home: " + e.getMessage());
            if (process != null) {
                process.destroy();
            }
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroy();
            }
            Thread.currentThread().interrupt();
        }
    }
}
