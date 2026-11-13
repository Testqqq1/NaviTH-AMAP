package com.navith.amap.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmapExitLabelTest {
    @Test
    public void joinsExitNameAndDirectionWithoutDuplicates() {
        assertEquals("ทางด่วน / บางนา",
                AmapExitLabel.join(new String[] {"ทางด่วน", " "}, new String[] {"บางนา", "ทางด่วน"}));
        assertEquals("", AmapExitLabel.join(null, new String[] {"", null}));
    }

    @Test
    public void truncatesLongSignText() {
        String longName = "X".repeat(100);
        String joined = AmapExitLabel.join(new String[] {longName}, new String[] {"extra"});
        assertTrue(joined.length() <= AmapExitLabel.MAX_LENGTH);
        assertTrue(joined.startsWith("XXXX"));
    }
}
