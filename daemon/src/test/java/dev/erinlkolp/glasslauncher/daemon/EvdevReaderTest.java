package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import org.junit.Before;
import org.junit.Test;

public class EvdevReaderTest {

    private EvdevReader reader;

    @Before
    public void setUp() {
        reader = new EvdevReader();
    }

    private static InputEvent abs(int code, int value) {
        return synthetic(InputEvent.EV_ABS, code, value);
    }

    private static InputEvent syn(int code) {
        return synthetic(InputEvent.EV_SYN, code, 0);
    }

    /** Builds an InputEvent through the real parser so tests exercise it too. */
    private static InputEvent synthetic(int type, int code, int value) {
        byte[] b = new byte[InputEvent.SIZE_BYTES];
        b[8] = (byte) (type & 0xFF);
        b[9] = (byte) ((type >> 8) & 0xFF);
        b[10] = (byte) (code & 0xFF);
        b[11] = (byte) ((code >> 8) & 0xFF);
        b[12] = (byte) (value & 0xFF);
        b[13] = (byte) ((value >> 8) & 0xFF);
        b[14] = (byte) ((value >> 16) & 0xFF);
        b[15] = (byte) ((value >> 24) & 0xFF);
        return InputEvent.parse(b, 0);
    }

    @Test
    public void incompleteFrameProducesNothing() {
        assertNull(reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683)));
        assertNull(reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93)));
    }

    @Test
    public void singleContactFrameProducesADownSample() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertNotNull(sample);
        assertEquals(TouchPhase.DOWN, sample.phase);
        assertEquals(1, sample.pointerCount);
    }

    /** Native 683 of 1366 is mid-pad, which is screen x 320 of 640. */
    @Test
    public void nativeCoordinatesAreScaledIntoScreenSpace() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(320.0f, sample.x, 1.0f);
        assertEquals(179.0f, sample.y, 2.0f);
    }

    @Test
    public void twoContactsInOneFrameReportPointerCountTwo() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 2));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 700));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 95));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(2, sample.pointerCount);
    }

    @Test
    public void secondFrameWithContactsIsAMove() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(syn(InputEvent.SYN_REPORT));

        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 650));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 95));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(TouchPhase.MOVE, sample.phase);
    }

    @Test
    public void emptyFrameAfterContactIsAnUp() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(syn(InputEvent.SYN_REPORT));

        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(TouchPhase.UP, sample.phase);
    }
}
