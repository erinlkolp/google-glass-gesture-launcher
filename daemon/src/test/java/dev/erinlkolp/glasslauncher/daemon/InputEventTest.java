package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class InputEventTest {

    /** tv_sec=1, tv_usec=500000, type=EV_ABS, code=ABS_MT_POSITION_X, value=683 */
    private static byte[] record() {
        return new byte[] {
                0x01, 0x00, 0x00, 0x00,          // tv_sec  = 1
                0x20, (byte) 0xA1, 0x07, 0x00,   // tv_usec = 500000
                0x03, 0x00,                      // type    = EV_ABS
                0x35, 0x00,                      // code    = ABS_MT_POSITION_X
                (byte) 0xAB, 0x02, 0x00, 0x00    // value   = 683
        };
    }

    @Test
    public void recordIsSixteenBytes() {
        assertEquals(16, InputEvent.SIZE_BYTES);
    }

    @Test
    public void parsesTypeCodeAndValue() {
        InputEvent event = InputEvent.parse(record(), 0);
        assertEquals(InputEvent.EV_ABS, event.type);
        assertEquals(InputEvent.ABS_MT_POSITION_X, event.code);
        assertEquals(683, event.value);
    }

    @Test
    public void combinesSecondsAndMicrosecondsIntoMilliseconds() {
        InputEvent event = InputEvent.parse(record(), 0);
        assertEquals(1500L, event.timeMs);
    }

    @Test
    public void parsesAtAnOffsetWithinALargerBuffer() {
        byte[] buffer = new byte[32];
        System.arraycopy(record(), 0, buffer, 16, 16);
        InputEvent event = InputEvent.parse(buffer, 16);
        assertEquals(InputEvent.ABS_MT_POSITION_X, event.code);
        assertEquals(683, event.value);
    }

    @Test
    public void parsesNegativeValues() {
        byte[] buffer = record();
        buffer[11] = 0x00;
        buffer[10] = 0x39;                       // code = ABS_MT_TRACKING_ID
        buffer[12] = (byte) 0xFF;
        buffer[13] = (byte) 0xFF;
        buffer[14] = (byte) 0xFF;
        buffer[15] = (byte) 0xFF;                // value = -1 (contact lifted)
        assertEquals(-1, InputEvent.parse(buffer, 0).value);
    }
}
