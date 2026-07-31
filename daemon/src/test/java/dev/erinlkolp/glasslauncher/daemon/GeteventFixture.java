package dev.erinlkolp.glasslauncher.daemon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses captured `getevent -lt` text into InputEvent instances. */
final class GeteventFixture {

    private static final Pattern LINE = Pattern.compile(
            "\\[\\s*([0-9.]+)\\]\\s+(EV_\\w+)\\s+(\\w+)\\s+(\\w+)");

    private static final int EV_SYN = 0x00;
    private static final int EV_ABS = 0x03;

    static List<InputEvent> load(String resource) throws IOException {
        List<InputEvent> events = new ArrayList<InputEvent>();
        InputStream in = GeteventFixture.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("fixture not found: " + resource);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = LINE.matcher(line);
                if (!m.find()) {
                    continue;
                }
                Integer type = typeOf(m.group(2));
                Integer code = codeOf(m.group(3));
                if (type == null || code == null) {
                    continue;
                }
                long micros = (long) (Double.parseDouble(m.group(1)) * 1_000_000.0);
                int value;
                try {
                    value = (int) Long.parseLong(m.group(4), 16);
                } catch (NumberFormatException notHex) {
                    value = 0;
                }
                events.add(synthesize(type, code, value, micros));
            }
        } finally {
            reader.close();
        }
        return events;
    }

    private static Integer typeOf(String name) {
        if ("EV_ABS".equals(name)) return EV_ABS;
        if ("EV_SYN".equals(name)) return EV_SYN;
        return null;
    }

    private static Integer codeOf(String name) {
        if ("ABS_MT_POSITION_X".equals(name)) return InputEvent.ABS_MT_POSITION_X;
        if ("ABS_MT_POSITION_Y".equals(name)) return InputEvent.ABS_MT_POSITION_Y;
        if ("ABS_MT_TRACKING_ID".equals(name)) return InputEvent.ABS_MT_TRACKING_ID;
        if ("SYN_MT_REPORT".equals(name)) return InputEvent.SYN_MT_REPORT;
        if ("SYN_REPORT".equals(name)) return InputEvent.SYN_REPORT;
        return null;
    }

    /** Builds the 16-byte record so InputEvent.parse is exercised by the replay too. */
    private static InputEvent synthesize(int type, int code, int value, long micros) {
        long sec = micros / 1_000_000L;
        long usec = micros % 1_000_000L;
        byte[] b = new byte[InputEvent.SIZE_BYTES];
        writeInt32(b, 0, (int) sec);
        writeInt32(b, 4, (int) usec);
        b[8] = (byte) (type & 0xFF);
        b[9] = (byte) ((type >> 8) & 0xFF);
        b[10] = (byte) (code & 0xFF);
        b[11] = (byte) ((code >> 8) & 0xFF);
        writeInt32(b, 12, value);
        return InputEvent.parse(b, 0);
    }

    private static void writeInt32(byte[] b, int o, int v) {
        b[o] = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
        b[o + 2] = (byte) ((v >> 16) & 0xFF);
        b[o + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private GeteventFixture() {
    }
}
