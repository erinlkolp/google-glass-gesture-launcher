package dev.erinlkolp.glasslauncher.daemon;

import java.io.IOException;

/** Sends the device to its home screen. Verified working from the shell. */
public final class HomeLauncher {

    private static final String[] COMMAND = {
            "am", "start",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME"
    };

    public void goHome() {
        try {
            Process process = Runtime.getRuntime().exec(COMMAND);
            process.waitFor();
        } catch (IOException e) {
            System.err.println("gestured: could not launch home: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
