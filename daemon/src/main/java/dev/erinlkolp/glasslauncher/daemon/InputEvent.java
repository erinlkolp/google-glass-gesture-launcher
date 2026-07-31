package dev.erinlkolp.glasslauncher.daemon;

/**
 * One {@code struct input_event} as delivered by the Linux evdev interface.
 *
 * <p>Layout on this device (32-bit ARM, little-endian), 16 bytes total:
 * {@code tv_sec} int32, {@code tv_usec} int32, {@code type} uint16,
 * {@code code} uint16, {@code value} int32.
 */
public final class InputEvent {

    public static final int SIZE_BYTES = 16;

    public static final int EV_SYN = 0x00;
    public static final int EV_KEY = 0x01;
    public static final int EV_ABS = 0x03;

    public static final int SYN_REPORT = 0x00;
    public static final int SYN_MT_REPORT = 0x02;

    public static final int ABS_MT_SLOT = 0x2f;
    public static final int ABS_MT_POSITION_X = 0x35;
    public static final int ABS_MT_POSITION_Y = 0x36;
    public static final int ABS_MT_TRACKING_ID = 0x39;

    public final int type;
    public final int code;
    public final int value;
    public final long timeMs;

    private InputEvent(int type, int code, int value, long timeMs) {
        this.type = type;
        this.code = code;
        this.value = value;
        this.timeMs = timeMs;
    }

    public static InputEvent parse(byte[] buffer, int offset) {
        long seconds = readInt32(buffer, offset);
        long micros = readInt32(buffer, offset + 4);
        int type = readUint16(buffer, offset + 8);
        int code = readUint16(buffer, offset + 10);
        int value = (int) readInt32(buffer, offset + 12);
        return new InputEvent(type, code, value, seconds * 1000L + micros / 1000L);
    }

    private static long readInt32(byte[] b, int o) {
        return (b[o] & 0xFFL)
                | ((b[o + 1] & 0xFFL) << 8)
                | ((b[o + 2] & 0xFFL) << 16)
                | ((long) b[o + 3] << 24);
    }

    private static int readUint16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }
}
