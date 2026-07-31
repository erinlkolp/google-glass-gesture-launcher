package dev.erinlkolp.glasslauncher.gesture;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TouchpadGeometryTest {

    private static final float EPS = 0.01f;

    @Test
    public void fullScreenWidthMapsToFullNativeWidth() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(1366.0f, g.toNativeX(640.0f), EPS);
    }

    @Test
    public void fullScreenHeightMapsToFullNativeHeight() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(187.0f, g.toNativeY(360.0f), EPS);
    }

    @Test
    public void originIsPreserved() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(0.0f, g.toNativeX(0.0f), EPS);
        assertEquals(0.0f, g.toNativeY(0.0f), EPS);
    }

    /**
     * The whole reason this class exists. One screen pixel of vertical travel
     * represents far less physical movement than one pixel of horizontal travel.
     */
    @Test
    public void verticalAxisIsCompressedRelativeToHorizontal() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        float horizontalUnitsPerPixel = g.toNativeX(1.0f);
        float verticalUnitsPerPixel = g.toNativeY(1.0f);
        assertEquals(2.134375f, horizontalUnitsPerPixel, EPS);
        assertEquals(0.519444f, verticalUnitsPerPixel, EPS);
        assertEquals(4.109f, horizontalUnitsPerPixel / verticalUnitsPerPixel, 0.01f);
    }
}
