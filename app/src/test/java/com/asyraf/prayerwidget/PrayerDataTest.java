package com.asyraf.prayerwidget;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PrayerDataTest {
    @Test public void parsesPrayerTime() {
        assertEquals(4 * 60 + 22, PrayerData.minutes("04:22"));
        assertEquals(-1, PrayerData.minutes("24:00"));
        assertEquals(-1, PrayerData.minutes("--:--"));
    }

    @Test public void cyclesPreviousNextNormal() {
        assertEquals(1, PrayerData.nextCycle(-1, true));
        assertEquals(0, PrayerData.nextCycle(-1, false));
        assertEquals(2, PrayerData.nextCycle(1, true));
        assertEquals(3, PrayerData.nextCycle(2, true));
        assertEquals(0, PrayerData.nextCycle(3, true));
    }
}
