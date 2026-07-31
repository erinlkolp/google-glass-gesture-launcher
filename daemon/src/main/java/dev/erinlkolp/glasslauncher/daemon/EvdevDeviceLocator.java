package dev.erinlkolp.glasslauncher.daemon;

/**
 * Resolves an input device node from the contents of
 * {@code /proc/bus/input/devices}, matching on device name rather than on a
 * hardcoded event number, which is not stable across boots.
 */
public final class EvdevDeviceLocator {

    private EvdevDeviceLocator() {
    }

    /** @return the node path such as {@code /dev/input/event3}, or null. */
    public static String findByName(String contents, String deviceName) {
        String needle = "Name=\"" + deviceName + "\"";
        boolean inStanza = false;
        for (String line : contents.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                inStanza = false;
                continue;
            }
            if (trimmed.startsWith("N:") && trimmed.contains(needle)) {
                inStanza = true;
                continue;
            }
            if (inStanza && trimmed.startsWith("H:")) {
                // Real captures look like "H: Handlers=event3" (no space
                // after '='), unlike some documented examples that show
                // multiple space-separated handlers (e.g. "kbd event3").
                // Splitting on '=' as well as whitespace handles both.
                for (String token : trimmed.substring(2).trim().split("[\\s=]+")) {
                    if (token.startsWith("event")) {
                        return "/dev/input/" + token;
                    }
                }
            }
        }
        return null;
    }
}
